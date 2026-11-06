package com.olehshklyar.lodestar.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Redis-backed leader election with a TTL lease (Master Poller pattern): only the leader polls
 * rate-limited external APIs.
 *
 * <ul>
 *   <li>Acquire: {@code SET key id NX PX ttl} -- atomic.</li>
 *   <li>Renew and release: Lua scripts that check the owner and change the key in one atomic step.
 *       Separate {@code GET} + {@code EXPIRE} / {@code DEL} would let an instance whose lease has just
 *       expired extend or delete the lease of the new leader.</li>
 *   <li>Local lease deadline: an instance considers itself leader only until {@code ttl} after it
 *       <em>sent</em> the last successful acquire / renew, so a pause longer than the TTL (GC, CPU
 *       throttling) ends leadership locally even before the next heartbeat.</li>
 * </ul>
 *
 * Limitation: there is no fencing token, so two instances can briefly both act as leader around a
 * lease change (clock drift, network delay). For polling an external API the cost is an extra request,
 * which is acceptable; operations that must never run twice need fencing on the receiving side.
 */
@Slf4j
@Service
public class RedisLeaderElectionService implements LeaderElectionService {

    public static final String LEADER_KEY = "lodestar:leader:alert-ingest";

    /** KEYS[1] = lease key, ARGV[1] = instance id, ARGV[2] = ttl in ms. Returns 1 if renewed. */
    static final RedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "return 0", Long.class);

    /** KEYS[1] = lease key, ARGV[1] = instance id. Returns 1 if this instance's lease was deleted. */
    static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) end "
                    + "return 0", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final Duration leaseTtl;
    private final boolean enabled;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private volatile long leaseDeadlineNanos;
    private LongSupplier nanoClock = System::nanoTime;

    public RedisLeaderElectionService(
            StringRedisTemplate redisTemplate,
            @Value("${lodestar.leader.instance-id:${HOSTNAME:${spring.application.name:lodestar}-${random.uuid}}}") String instanceId,
            @Value("${lodestar.leader.lease-ttl:20s}") Duration leaseTtl,
            @Value("${lodestar.leader.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
        this.leaseTtl = leaseTtl;
        this.enabled = enabled;
        log.info("Initialized RedisLeaderElectionService with instanceId: [{}], leaseTtl: [{}], enabled: [{}]",
                this.instanceId, this.leaseTtl, this.enabled);
    }

    /** Test hook: replaces the monotonic clock used for the local lease deadline. */
    void setNanoClock(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean isLeader() {
        return enabled && isLeader.get() && nanoClock.getAsLong() - leaseDeadlineNanos < 0;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Scheduled(fixedRateString = "${lodestar.leader.heartbeat-interval:7000}")
    public void scheduledHeartbeat() {
        if (!enabled) {
            return;
        }
        tryAcquireOrRenewLease();
    }

    @Override
    public synchronized boolean tryAcquireOrRenewLease() {
        if (!enabled) {
            isLeader.set(false);
            return false;
        }
        // The lease is counted from before the request: Redis may set the TTL later, never earlier.
        long sentAt = nanoClock.getAsLong();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LEADER_KEY, instanceId, leaseTtl);
            if (Boolean.TRUE.equals(acquired)) {
                holdLease(sentAt, "acquired");
                return true;
            }
            Long renewed = redisTemplate.execute(RENEW_SCRIPT, List.of(LEADER_KEY),
                    instanceId, Long.toString(leaseTtl.toMillis()));
            if (Long.valueOf(1).equals(renewed)) {
                holdLease(sentAt, "renewed");
                return true;
            }
            if (isLeader.getAndSet(false)) {
                log.info("Instance [{}] stepped down: the lease for [{}] is held by another instance", instanceId, LEADER_KEY);
            }
            return false;
        } catch (Exception e) {
            log.debug("Redis connectivity issue during leader election: {}. Stepping down to avoid split-brain.", e.getMessage());
            isLeader.set(false);
            return false;
        }
    }

    private void holdLease(long sentAt, String how) {
        leaseDeadlineNanos = sentAt + leaseTtl.toNanos();
        if (!isLeader.getAndSet(true)) {
            log.info("Instance [{}] {} LEADER lease for [{}]", instanceId, how, LEADER_KEY);
        }
    }

    @Override
    @PreDestroy
    public synchronized void releaseLease() {
        if (!enabled) {
            return;
        }
        try {
            Long released = redisTemplate.execute(RELEASE_SCRIPT, List.of(LEADER_KEY), instanceId);
            if (Long.valueOf(1).equals(released)) {
                log.info("Instance [{}] released leader lease on shutdown.", instanceId);
            }
        } catch (Exception e) {
            log.debug("Could not release leader lease on shutdown (connection factory likely already stopped): {}", e.getMessage());
        } finally {
            isLeader.set(false);
        }
    }
}

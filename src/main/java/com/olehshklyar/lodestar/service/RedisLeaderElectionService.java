package com.olehshklyar.lodestar.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed implementation of distributed leader election using atomic SETNX and sliding TTL leases.
 * Prevents multiple pods from simultaneously polling rate-limited external APIs (Master Poller pattern).
 */
@Slf4j
@Service
public class RedisLeaderElectionService implements LeaderElectionService {

    public static final String LEADER_KEY = "lodestar:leader:alert-ingest";

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final Duration leaseTtl;
    private final boolean enabled;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

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

    @Override
    public boolean isLeader() {
        return enabled && isLeader.get();
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
        try {
            // 1. Try to acquire lease via atomic SETNX with TTL
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LEADER_KEY, instanceId, leaseTtl);
            if (Boolean.TRUE.equals(acquired)) {
                if (!isLeader.getAndSet(true)) {
                    log.info("Instance [{}] successfully acquired LEADER lease for [{}]", instanceId, LEADER_KEY);
                }
                return true;
            }

            // 2. If key exists, check if this instance is the current leader and renew
            String currentLeader = redisTemplate.opsForValue().get(LEADER_KEY);
            if (instanceId.equals(currentLeader)) {
                redisTemplate.expire(LEADER_KEY, leaseTtl);
                if (!isLeader.getAndSet(true)) {
                    log.info("Instance [{}] renewed LEADER lease for [{}]", instanceId, LEADER_KEY);
                }
                return true;
            } else {
                if (isLeader.getAndSet(false)) {
                    log.info("Instance [{}] stepped down as leader. Active leader is [{}]", instanceId, currentLeader);
                }
                return false;
            }
        } catch (Exception e) {
            log.debug("Redis connectivity issue during leader election: {}. Stepping down to avoid split-brain.", e.getMessage());
            isLeader.set(false);
            return false;
        }
    }

    @Override
    @PreDestroy
    public synchronized void releaseLease() {
        if (!enabled) {
            return;
        }
        try {
            String currentLeader = redisTemplate.opsForValue().get(LEADER_KEY);
            if (instanceId.equals(currentLeader)) {
                redisTemplate.delete(LEADER_KEY);
                log.info("Instance [{}] released leader lease on shutdown.", instanceId);
            }
        } catch (Exception e) {
            log.debug("Could not release leader lease on shutdown (connection factory likely already stopped): {}", e.getMessage());
        } finally {
            isLeader.set(false);
        }
    }
}

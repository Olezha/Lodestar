package com.olehshklyar.lodestar.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static com.olehshklyar.lodestar.service.RedisLeaderElectionService.LEADER_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the Lua scripts of {@link RedisLeaderElectionService} against a real Redis: an instance whose
 * lease has expired and passed to another instance must neither renew nor delete the new lease.
 */
@Testcontainers
class RedisLeaderElectionIntegrationTest {

    static {
        System.setProperty("api.version", "1.44");
        System.setProperty("docker.client.api.version", "1.44");
    }

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clean() {
        template.delete(LEADER_KEY);
    }

    private RedisLeaderElectionService instance(String id, Duration ttl) {
        return new RedisLeaderElectionService(template, id, ttl, true);
    }

    @Test
    @DisplayName("Only one instance holds the lease; the other can neither renew nor release it")
    void onlyOneLeader() {
        RedisLeaderElectionService a = instance("pod-a", Duration.ofSeconds(10));
        RedisLeaderElectionService b = instance("pod-b", Duration.ofSeconds(10));

        assertThat(a.tryAcquireOrRenewLease()).isTrue();
        assertThat(b.tryAcquireOrRenewLease()).isFalse();
        b.releaseLease();

        assertThat(template.opsForValue().get(LEADER_KEY)).isEqualTo("pod-a");
        assertThat(a.isLeader()).isTrue();
        assertThat(b.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Renewal extends the TTL of the holder's own lease")
    void renewalExtendsTtl() throws InterruptedException {
        RedisLeaderElectionService a = instance("pod-a", Duration.ofSeconds(2));
        assertThat(a.tryAcquireOrRenewLease()).isTrue();

        Thread.sleep(1200);
        assertThat(a.tryAcquireOrRenewLease()).isTrue();

        Long ttlMs = template.getExpire(LEADER_KEY, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(ttlMs).isGreaterThan(1500L);
    }

    @Test
    @DisplayName("After the lease expires and passes to another instance, the old holder cannot renew or delete it")
    void expiredHolderCannotTouchNewLease() throws InterruptedException {
        RedisLeaderElectionService a = instance("pod-a", Duration.ofMillis(800));
        RedisLeaderElectionService b = instance("pod-b", Duration.ofSeconds(10));
        assertThat(a.tryAcquireOrRenewLease()).isTrue();

        Thread.sleep(1100); // pod-a "paused" longer than its TTL
        assertThat(a.isLeader()).isFalse(); // local deadline ended leadership without a heartbeat
        assertThat(b.tryAcquireOrRenewLease()).isTrue();

        assertThat(a.tryAcquireOrRenewLease()).isFalse();
        a.releaseLease();

        assertThat(template.opsForValue().get(LEADER_KEY)).isEqualTo("pod-b");
        assertThat(template.getExpire(LEADER_KEY)).isGreaterThan(5L);
        assertThat(b.isLeader()).isTrue();
    }

    @Test
    @DisplayName("The holder releases its own lease on shutdown, so another instance can take over at once")
    void holderReleasesOwnLease() {
        RedisLeaderElectionService a = instance("pod-a", Duration.ofSeconds(10));
        RedisLeaderElectionService b = instance("pod-b", Duration.ofSeconds(10));
        assertThat(a.tryAcquireOrRenewLease()).isTrue();

        a.releaseLease();

        assertThat(template.hasKey(LEADER_KEY)).isFalse();
        assertThat(b.tryAcquireOrRenewLease()).isTrue();
    }
}

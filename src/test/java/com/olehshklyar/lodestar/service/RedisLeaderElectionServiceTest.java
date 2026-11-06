package com.olehshklyar.lodestar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.olehshklyar.lodestar.service.RedisLeaderElectionService.LEADER_KEY;
import static com.olehshklyar.lodestar.service.RedisLeaderElectionService.RELEASE_SCRIPT;
import static com.olehshklyar.lodestar.service.RedisLeaderElectionService.RENEW_SCRIPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLeaderElectionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLeaderElectionService service;
    private final String instanceId = "pod-test-1";
    private final Duration leaseTtl = Duration.ofSeconds(20);
    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisLeaderElectionService(redisTemplate, instanceId, leaseTtl, true);
        service.setNanoClock(now::get);
    }

    private void keyHeldByOther() {
        when(valueOperations.setIfAbsent(eq(LEADER_KEY), eq(instanceId), eq(leaseTtl))).thenReturn(false);
    }

    private void renewResult(long result) {
        when(redisTemplate.execute(eq(RENEW_SCRIPT), eq(List.of(LEADER_KEY)), eq(instanceId), eq("20000")))
                .thenReturn(result);
    }

    @Test
    @DisplayName("Should successfully acquire leadership when key is absent in Redis")
    void shouldAcquireLeaseWhenKeyAbsent() {
        when(valueOperations.setIfAbsent(eq(LEADER_KEY), eq(instanceId), eq(leaseTtl))).thenReturn(true);

        boolean acquired = service.tryAcquireOrRenewLease();

        assertThat(acquired).isTrue();
        assertThat(service.isLeader()).isTrue();
        assertThat(service.getInstanceId()).isEqualTo(instanceId);
    }

    @Test
    @DisplayName("Should renew the lease atomically (owner check and PEXPIRE in one script) when this instance holds it")
    void shouldRenewLeaseWhenKeyMatchesInstanceId() {
        keyHeldByOther();
        renewResult(1L);

        boolean renewed = service.tryAcquireOrRenewLease();

        assertThat(renewed).isTrue();
        assertThat(service.isLeader()).isTrue();
        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    @DisplayName("Should step down from leadership when another instance holds the lease")
    void shouldStepDownWhenAnotherInstanceHoldsLease() {
        when(valueOperations.setIfAbsent(eq(LEADER_KEY), eq(instanceId), eq(leaseTtl))).thenReturn(true);
        service.tryAcquireOrRenewLease();
        keyHeldByOther();
        renewResult(0L);

        boolean result = service.tryAcquireOrRenewLease();

        assertThat(result).isFalse();
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should stop being leader locally once the TTL has passed since the last successful renewal")
    void shouldExpireLeadershipLocallyAfterTtl() {
        when(valueOperations.setIfAbsent(eq(LEADER_KEY), eq(instanceId), eq(leaseTtl))).thenReturn(true);
        service.tryAcquireOrRenewLease();

        now.addAndGet(leaseTtl.toNanos() - 1);
        assertThat(service.isLeader()).isTrue();

        now.addAndGet(1);
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should count the lease from the moment the request was sent, not from the reply")
    void shouldCountLeaseFromRequestTime() {
        when(valueOperations.setIfAbsent(eq(LEADER_KEY), eq(instanceId), eq(leaseTtl))).thenAnswer(invocation -> {
            now.addAndGet(Duration.ofSeconds(5).toNanos()); // slow reply
            return true;
        });
        service.tryAcquireOrRenewLease();

        now.addAndGet(Duration.ofSeconds(15).toNanos()); // 20 s after the request, 15 s after the reply
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should gracefully step down and return false during Redis connection failure (split-brain protection)")
    void shouldHandleRedisExceptionGracefully() {
        when(valueOperations.setIfAbsent(any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        boolean result = service.tryAcquireOrRenewLease();

        assertThat(result).isFalse();
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should release the lease with the owner-checking script on shutdown")
    void shouldReleaseLeaseOnShutdownIfCurrentLeader() {
        when(redisTemplate.execute(eq(RELEASE_SCRIPT), eq(List.of(LEADER_KEY)), eq(instanceId))).thenReturn(1L);

        service.releaseLease();

        verify(redisTemplate).execute(eq(RELEASE_SCRIPT), eq(List.of(LEADER_KEY)), eq(instanceId));
        verify(redisTemplate, never()).delete(any(String.class));
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should step down on shutdown even if the release script finds another owner")
    void shouldStepDownOnShutdownIfNotLeader() {
        when(redisTemplate.execute(eq(RELEASE_SCRIPT), anyList(), eq(instanceId))).thenReturn(0L);

        service.releaseLease();

        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should immediately return false when leader election is disabled")
    void shouldReturnFalseWhenDisabled() {
        RedisLeaderElectionService disabledService = new RedisLeaderElectionService(
                redisTemplate, instanceId, leaseTtl, false
        );

        assertThat(disabledService.isLeader()).isFalse();
        assertThat(disabledService.tryAcquireOrRenewLease()).isFalse();
    }
}

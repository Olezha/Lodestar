package com.olehshklyar.lodestar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisLeaderElectionService(redisTemplate, instanceId, leaseTtl, true);
    }

    @Test
    @DisplayName("Should successfully acquire leadership when key is absent in Redis")
    void shouldAcquireLeaseWhenKeyAbsent() {
        when(valueOperations.setIfAbsent(eq(RedisLeaderElectionService.LEADER_KEY), eq(instanceId), eq(leaseTtl)))
                .thenReturn(true);

        boolean acquired = service.tryAcquireOrRenewLease();

        assertThat(acquired).isTrue();
        assertThat(service.isLeader()).isTrue();
        assertThat(service.getInstanceId()).isEqualTo(instanceId);
    }

    @Test
    @DisplayName("Should renew leadership TTL when key is already held by this instance")
    void shouldRenewLeaseWhenKeyMatchesInstanceId() {
        when(valueOperations.setIfAbsent(eq(RedisLeaderElectionService.LEADER_KEY), eq(instanceId), eq(leaseTtl)))
                .thenReturn(false);
        when(valueOperations.get(RedisLeaderElectionService.LEADER_KEY)).thenReturn(instanceId);

        boolean renewed = service.tryAcquireOrRenewLease();

        assertThat(renewed).isTrue();
        assertThat(service.isLeader()).isTrue();
        verify(redisTemplate).expire(RedisLeaderElectionService.LEADER_KEY, leaseTtl);
    }

    @Test
    @DisplayName("Should step down from leadership when another instance holds the lease")
    void shouldStepDownWhenAnotherInstanceHoldsLease() {
        when(valueOperations.setIfAbsent(eq(RedisLeaderElectionService.LEADER_KEY), eq(instanceId), eq(leaseTtl)))
                .thenReturn(false);
        when(valueOperations.get(RedisLeaderElectionService.LEADER_KEY)).thenReturn("pod-other-2");

        boolean result = service.tryAcquireOrRenewLease();

        assertThat(result).isFalse();
        assertThat(service.isLeader()).isFalse();
        verify(redisTemplate, never()).expire(any(), any());
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
    @DisplayName("Should cleanly release lease on shutdown if this instance is the current leader")
    void shouldReleaseLeaseOnShutdownIfCurrentLeader() {
        when(valueOperations.get(RedisLeaderElectionService.LEADER_KEY)).thenReturn(instanceId);

        service.releaseLease();

        verify(redisTemplate).delete(RedisLeaderElectionService.LEADER_KEY);
        assertThat(service.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should not delete lease key on shutdown if another instance is the leader")
    void shouldNotDeleteKeyOnShutdownIfNotLeader() {
        when(valueOperations.get(RedisLeaderElectionService.LEADER_KEY)).thenReturn("pod-other-2");

        service.releaseLease();

        verify(redisTemplate, never()).delete(anyString());
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

package com.olehshklyar.lodestar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("Should return true when task lock is acquired for the first time")
    void shouldReturnTrueWhenTaskLockIsAcquiredFirstTime() {
        // Arrange
        String taskId = "task-123";
        Duration ttl = Duration.ofMinutes(15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("dedup:notification:" + taskId, "PROCESSED", ttl)).thenReturn(true);

        // Act
        boolean acquired = idempotencyService.acquireLock(taskId, ttl);

        // Assert
        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("dedup:notification:" + taskId, "PROCESSED", ttl);
    }

    @Test
    @DisplayName("Should return false when task lock already exists (duplicate task)")
    void shouldReturnFalseWhenTaskLockAlreadyExists() {
        // Arrange
        String taskId = "task-duplicate";
        Duration ttl = Duration.ofMinutes(15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("dedup:notification:" + taskId, "PROCESSED", ttl)).thenReturn(false);

        // Act
        boolean acquired = idempotencyService.acquireLock(taskId, ttl);

        // Assert
        assertThat(acquired).isFalse();
    }
}

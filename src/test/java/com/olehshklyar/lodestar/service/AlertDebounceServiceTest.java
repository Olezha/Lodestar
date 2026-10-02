package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertDebounceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AlertSourceProperties properties;
    private AlertDebounceService debounceService;

    private static final String REGION_ID = "KYIV_REGION";
    private static final String EVENT_TYPE = "AIR_RAID";
    private static final String DEBOUNCE_KEY = AlertDebounceService.DEBOUNCE_KEY_PREFIX + REGION_ID + ":" + EVENT_TYPE;
    private static final Duration TTL = Duration.ofHours(1);

    @BeforeEach
    void setUp() {
        properties = new AlertSourceProperties("live", "https://api.alerts.in.ua/v1", "token", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(5), TTL);
        debounceService = new AlertDebounceService(redisTemplate, properties);
    }

    @Test
    @DisplayName("Should return true when alert is seen for the first time")
    void shouldReturnTrueWhenFirstTime() {
        // Arrange
        AlertEvent event = createEvent(REGION_ID, EVENT_TYPE);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(DEBOUNCE_KEY), eq(event.eventId()), eq(TTL))).thenReturn(true);

        // Act
        boolean shouldPublish = debounceService.shouldPublish(event);

        // Assert
        assertThat(shouldPublish).isTrue();
        verify(valueOperations).setIfAbsent(eq(DEBOUNCE_KEY), eq(event.eventId()), eq(TTL));
    }

    @Test
    @DisplayName("Should return false when alert is already active within debounce TTL")
    void shouldReturnFalseWhenAlreadyActive() {
        // Arrange
        AlertEvent event = createEvent(REGION_ID, EVENT_TYPE);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(DEBOUNCE_KEY), eq(event.eventId()), eq(TTL))).thenReturn(false);

        // Act
        boolean shouldPublish = debounceService.shouldPublish(event);

        // Assert
        assertThat(shouldPublish).isFalse();
    }

    @Test
    @DisplayName("Should fall back to in-memory tracking when Redis fails")
    void shouldFallBackToInMemoryWhenRedisFails() {
        // Arrange
        AlertEvent event1 = createEvent(REGION_ID, EVENT_TYPE);
        AlertEvent event2 = createEvent(REGION_ID, EVENT_TYPE);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis connection timed out"));

        // Act: first call records in memory, second call is debounced
        boolean firstCall = debounceService.shouldPublish(event1);
        boolean secondCall = debounceService.shouldPublish(event2);

        // Assert
        assertThat(firstCall).isTrue();
        assertThat(secondCall).isFalse();
    }

    @Test
    @DisplayName("Should always return true when debounce TTL is zero or negative")
    void shouldAlwaysReturnTrueWhenTtlZero() {
        // Arrange
        AlertSourceProperties zeroTtlProps = new AlertSourceProperties("live", "https://api.alerts.in.ua/v1", "token", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO);
        AlertDebounceService zeroTtlService = new AlertDebounceService(redisTemplate, zeroTtlProps);

        AlertEvent event = createEvent(REGION_ID, EVENT_TYPE);

        // Act & Assert
        assertThat(zeroTtlService.shouldPublish(event)).isTrue();
    }

    @Test
    @DisplayName("Should clear active alert key on demand")
    void shouldClearActiveAlert() {
        // Act
        debounceService.clearActiveAlert(REGION_ID, EVENT_TYPE);

        // Assert
        verify(redisTemplate).delete(DEBOUNCE_KEY);
    }

    private AlertEvent createEvent(String regionId, String eventType) {
        return new AlertEvent(
                UUID.randomUUID().toString(),
                regionId,
                eventType,
                "ACTIVE",
                "CRITICAL",
                Instant.now()
        );
    }
}

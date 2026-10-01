package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.config.AnalyticsProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.dto.AnomalyEvent;
import com.olehshklyar.lodestar.entity.AnomalyEventHistory;
import com.olehshklyar.lodestar.producer.AnomalyEventProducer;
import com.olehshklyar.lodestar.repository.AnomalyEventHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamAnalysisEngineTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AnomalyEventHistoryRepository anomalyHistoryRepository;

    @Mock
    private AnomalyEventProducer anomalyEventProducer;

    private AnalyticsProperties analyticsProperties;
    private StreamAnalysisEngine streamAnalysisEngine;

    private static final String REGION_ID = "KYIV_REGION";

    @BeforeEach
    void setUp() {
        analyticsProperties = new AnalyticsProperties(
                Duration.ofMinutes(5),
                3, // threshold: 3 events
                Duration.ofMinutes(10), // cooldown: 10 mins
                true
        );

        streamAnalysisEngine = new StreamAnalysisEngine(
                analyticsProperties,
                redisTemplate,
                anomalyHistoryRepository,
                anomalyEventProducer
        );
    }

    @Test
    @DisplayName("Should not trigger anomaly when sliding window count is below threshold")
    void shouldNotTriggerAnomalyBelowThreshold() {
        // Arrange
        AlertEvent event = createAlertEvent(REGION_ID);
        String windowKey = StreamAnalysisEngine.WINDOW_KEY_PREFIX + REGION_ID;

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(windowKey)).thenReturn(2L); // 2 events < threshold 3

        // Act
        Optional<AnomalyEvent> result = streamAnalysisEngine.analyzeEvent(event);

        // Assert
        assertThat(result).isEmpty();
        verify(anomalyHistoryRepository, never()).save(any());
        verify(anomalyEventProducer, never()).sendAnomalyEvent(any());
    }

    @Test
    @DisplayName("Should detect anomaly, archive to DB, and publish to Kafka when threshold is reached and lock acquired")
    void shouldTriggerAnomalyWhenThresholdReached() {
        // Arrange
        AlertEvent event = createAlertEvent(REGION_ID);
        String windowKey = StreamAnalysisEngine.WINDOW_KEY_PREFIX + REGION_ID;
        String cooldownKey = StreamAnalysisEngine.COOLDOWN_KEY_PREFIX + REGION_ID;

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(windowKey)).thenReturn(3L); // 3 events == threshold 3
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(cooldownKey), eq("ACTIVE"), eq(Duration.ofMinutes(10)))).thenReturn(true);

        // Act
        Optional<AnomalyEvent> result = streamAnalysisEngine.analyzeEvent(event);

        // Assert
        assertThat(result).isPresent();
        AnomalyEvent anomaly = result.get();
        assertThat(anomaly.regionId()).isEqualTo(REGION_ID);
        assertThat(anomaly.anomalyType()).isEqualTo(StreamAnalysisEngine.ANOMALY_TYPE_SPIKE);
        assertThat(anomaly.eventCount()).isEqualTo(3);
        assertThat(anomaly.severity()).isEqualTo("CRITICAL");

        // Verify archived to DB
        ArgumentCaptor<AnomalyEventHistory> historyCaptor = ArgumentCaptor.forClass(AnomalyEventHistory.class);
        verify(anomalyHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAnomalyId()).isEqualTo(anomaly.anomalyId());
        assertThat(historyCaptor.getValue().getRegionId()).isEqualTo(REGION_ID);

        // Verify published to Kafka
        verify(anomalyEventProducer).sendAnomalyEvent(anomaly);
    }

    @Test
    @DisplayName("Should suppress anomaly when threshold is breached but cooldown lock is active")
    void shouldSuppressAnomalyDuringCooldown() {
        // Arrange
        AlertEvent event = createAlertEvent(REGION_ID);
        String windowKey = StreamAnalysisEngine.WINDOW_KEY_PREFIX + REGION_ID;
        String cooldownKey = StreamAnalysisEngine.COOLDOWN_KEY_PREFIX + REGION_ID;

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(windowKey)).thenReturn(4L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(cooldownKey), eq("ACTIVE"), eq(Duration.ofMinutes(10)))).thenReturn(false);

        // Act
        Optional<AnomalyEvent> result = streamAnalysisEngine.analyzeEvent(event);

        // Assert
        assertThat(result).isEmpty();
        verify(anomalyHistoryRepository, never()).save(any());
        verify(anomalyEventProducer, never()).sendAnomalyEvent(any());
    }

    @Test
    @DisplayName("Should fall back to in-memory window and detect anomaly when Redis is down")
    void shouldFallBackToInMemoryWindowWhenRedisDown() {
        // Arrange: make Redis throw exception
        when(redisTemplate.opsForZSet()).thenThrow(new RedisConnectionFailureException("Redis connection refused"));
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        // Act: send 3 events for REGION_ID
        Optional<AnomalyEvent> result1 = streamAnalysisEngine.analyzeEvent(createAlertEvent(REGION_ID));
        Optional<AnomalyEvent> result2 = streamAnalysisEngine.analyzeEvent(createAlertEvent(REGION_ID));
        Optional<AnomalyEvent> result3 = streamAnalysisEngine.analyzeEvent(createAlertEvent(REGION_ID));

        // Assert: event 1 and 2 are under threshold, event 3 triggers anomaly
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
        assertThat(result3).isPresent();

        AnomalyEvent anomaly = result3.get();
        assertThat(anomaly.regionId()).isEqualTo(REGION_ID);
        assertThat(anomaly.eventCount()).isEqualTo(3);
        verify(anomalyEventProducer).sendAnomalyEvent(anomaly);
    }

    @Test
    @DisplayName("Should return empty when analytics is disabled")
    void shouldReturnEmptyWhenDisabled() {
        // Arrange
        AnalyticsProperties disabledProperties = new AnalyticsProperties(Duration.ofMinutes(5), 3, Duration.ofMinutes(10), false);
        StreamAnalysisEngine disabledEngine = new StreamAnalysisEngine(
                disabledProperties, redisTemplate, anomalyHistoryRepository, anomalyEventProducer
        );

        // Act
        Optional<AnomalyEvent> result = disabledEngine.analyzeEvent(createAlertEvent(REGION_ID));

        // Assert
        assertThat(result).isEmpty();
        verify(anomalyEventProducer, never()).sendAnomalyEvent(any());
    }

    private AlertEvent createAlertEvent(String regionId) {
        return new AlertEvent(
                UUID.randomUUID().toString(),
                regionId,
                "AIR_RAID",
                "ACTIVE",
                "CRITICAL",
                Instant.now()
        );
    }
}

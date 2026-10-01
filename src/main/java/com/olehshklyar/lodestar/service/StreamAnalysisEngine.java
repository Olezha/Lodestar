package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.config.AnalyticsProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.dto.AnomalyEvent;
import com.olehshklyar.lodestar.entity.AnomalyEventHistory;
import com.olehshklyar.lodestar.producer.AnomalyEventProducer;
import com.olehshklyar.lodestar.repository.AnomalyEventHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Stream Analysis Engine that analyzes incoming alert streams using sliding time windows.
 * Detects rapid-fire spikes and multi-event anomalies per region and publishes enriched AnomalyEvents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamAnalysisEngine {

    public static final String WINDOW_KEY_PREFIX = "lodestar:analytics:window:";
    public static final String COOLDOWN_KEY_PREFIX = "lodestar:analytics:cooldown:";
    public static final String ANOMALY_TYPE_SPIKE = "RAPID_FIRE_SPIKE";

    private final AnalyticsProperties analyticsProperties;
    private final StringRedisTemplate redisTemplate;
    private final AnomalyEventHistoryRepository anomalyHistoryRepository;
    private final AnomalyEventProducer anomalyEventProducer;

    // In-memory fallback structures for sliding window & cooldown when Redis is unavailable
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> inMemoryWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> inMemoryCooldowns = new ConcurrentHashMap<>();

    /**
     * Ingests and evaluates an incoming AlertEvent within the configured sliding time window.
     *
     * @param event incoming raw alert event
     * @return Optional containing detected AnomalyEvent if a threshold breach occurred, else empty
     */
    public Optional<AnomalyEvent> analyzeEvent(AlertEvent event) {
        if (!analyticsProperties.enabled() || event == null || event.regionId() == null) {
            return Optional.empty();
        }

        String regionId = event.regionId();
        long now = Instant.now().toEpochMilli();
        long windowStart = now - analyticsProperties.windowDuration().toMillis();

        long eventCount = recordEventAndCount(regionId, event.eventId(), now, windowStart);
        log.debug("Region [{}] sliding window count: {} (threshold: {})",
                regionId, eventCount, analyticsProperties.spikeThreshold());

        if (eventCount >= analyticsProperties.spikeThreshold()) {
            if (tryAcquireCooldownLock(regionId)) {
                AnomalyEvent anomaly = triggerAnomaly(event, eventCount);
                return Optional.of(anomaly);
            } else {
                log.debug("Anomaly spike for region [{}] suppressed by cooldown window ({}s)",
                        regionId, analyticsProperties.cooldownDuration().toSeconds());
            }
        }

        return Optional.empty();
    }

    private long recordEventAndCount(String regionId, String eventId, long now, long windowStart) {
        String windowKey = WINDOW_KEY_PREFIX + regionId;
        try {
            // Redis ZSet sliding window: score is epoch millis
            redisTemplate.opsForZSet().add(windowKey, eventId + ":" + now, (double) now);
            redisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, (double) (windowStart - 1));
            redisTemplate.expire(windowKey, analyticsProperties.windowDuration().multipliedBy(2));

            Long count = redisTemplate.opsForZSet().zCard(windowKey);
            return count != null ? count : 1L;
        } catch (Exception ex) {
            log.warn("Redis sliding window failed for region [{}]. Degrading to in-memory window: {}",
                    regionId, ex.getMessage());
            return recordInMemory(regionId, now, windowStart);
        }
    }

    private long recordInMemory(String regionId, long now, long windowStart) {
        ConcurrentLinkedDeque<Long> deque = inMemoryWindows.computeIfAbsent(regionId, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(now);

        // Evict expired entries
        while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
            deque.pollFirst();
        }
        return deque.size();
    }

    private boolean tryAcquireCooldownLock(String regionId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + regionId;
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "ACTIVE", analyticsProperties.cooldownDuration());
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("Redis cooldown lock failed for region [{}]. Degrading to in-memory lock: {}",
                    regionId, ex.getMessage());
            Instant lastTrigger = inMemoryCooldowns.get(regionId);
            Instant now = Instant.now();
            if (lastTrigger == null || now.isAfter(lastTrigger.plus(analyticsProperties.cooldownDuration()))) {
                inMemoryCooldowns.put(regionId, now);
                return true;
            }
            return false;
        }
    }

    private AnomalyEvent triggerAnomaly(AlertEvent event, long eventCount) {
        String anomalyId = UUID.randomUUID().toString();
        Instant detectedAt = Instant.now();
        String description = String.format("Rapid-fire alert spike detected in %s: %d events within %d seconds (threshold: %d)",
                event.regionId(),
                eventCount,
                analyticsProperties.windowDuration().toSeconds(),
                analyticsProperties.spikeThreshold());

        AnomalyEvent anomaly = new AnomalyEvent(
                anomalyId,
                event.regionId(),
                ANOMALY_TYPE_SPIKE,
                description,
                (int) eventCount,
                analyticsProperties.windowDuration().toSeconds(),
                "CRITICAL",
                detectedAt,
                List.of(event.eventId())
        );

        // 1. Archive to relational database
        try {
            AnomalyEventHistory historyRecord = AnomalyEventHistory.builder()
                    .anomalyId(anomaly.anomalyId())
                    .regionId(anomaly.regionId())
                    .anomalyType(anomaly.anomalyType())
                    .description(anomaly.description())
                    .eventCount(anomaly.eventCount())
                    .windowDurationSeconds(anomaly.windowDurationSeconds())
                    .severity(anomaly.severity())
                    .detectedAt(anomaly.detectedAt())
                    .build();
            anomalyHistoryRepository.save(historyRecord);
            log.info("Archived AnomalyEvent [id={}] to anomaly_events_history log", anomalyId);
        } catch (Exception ex) {
            log.error("Failed to archive AnomalyEvent [id={}] to database", anomalyId, ex);
        }

        // 2. Publish to Kafka topic events.anomalies
        anomalyEventProducer.sendAnomalyEvent(anomaly);

        return anomaly;
    }
}

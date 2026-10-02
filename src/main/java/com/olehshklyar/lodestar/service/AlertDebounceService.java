package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that debounces repetitive active alert events during continuous polling.
 * Prevents flooding Kafka and notification channels with duplicate messages for ongoing alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDebounceService {

    public static final String DEBOUNCE_KEY_PREFIX = "lodestar:alert:active:";

    private final StringRedisTemplate redisTemplate;
    private final AlertSourceProperties properties;

    // In-memory fallback tracking when Redis is unavailable
    private final ConcurrentHashMap<String, Instant> inMemorySeenAlerts = new ConcurrentHashMap<>();

    /**
     * Checks if the alert event should be published or suppressed as a duplicate.
     *
     * @param event the alert event to evaluate
     * @return true if the event is new and should be published; false if already active/seen
     */
    public boolean shouldPublish(AlertEvent event) {
        if (event == null || event.regionId() == null || event.eventType() == null) {
            return false;
        }

        Duration ttl = properties.debounceTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return true;
        }

        String alertKey = buildDebounceKey(event.regionId(), event.eventType());

        // 1. Try Redis SETNX
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(alertKey, event.eventId(), ttl);
            boolean shouldPublish = Boolean.TRUE.equals(isNew);
            if (!shouldPublish) {
                log.debug("Debouncing duplicate alert for region [{}] type [{}]", event.regionId(), event.eventType());
            }
            return shouldPublish;
        } catch (Exception ex) {
            log.warn("Redis debouncing check failed for key [{}]. Degrading to in-memory state: {}",
                    alertKey, ex.getMessage());
            return checkInMemory(alertKey, ttl);
        }
    }

    /**
     * Manually clears the active state for a region and event type (e.g. when an alert officially ends).
     */
    public void clearActiveAlert(String regionId, String eventType) {
        String alertKey = buildDebounceKey(regionId, eventType);
        try {
            redisTemplate.delete(alertKey);
        } catch (Exception ex) {
            log.warn("Failed to delete debouncing key from Redis [{}]: {}", alertKey, ex.getMessage());
        }
        inMemorySeenAlerts.remove(alertKey);
    }

    private boolean checkInMemory(String alertKey, Duration ttl) {
        Instant now = Instant.now();
        Instant existingExpiry = inMemorySeenAlerts.get(alertKey);

        if (existingExpiry != null && now.isBefore(existingExpiry)) {
            return false;
        }

        inMemorySeenAlerts.put(alertKey, now.plus(ttl));
        return true;
    }

    private String buildDebounceKey(String regionId, String eventType) {
        return DEBOUNCE_KEY_PREFIX + regionId + ":" + eventType;
    }
}

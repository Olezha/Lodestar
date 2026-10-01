package com.olehshklyar.lodestar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Service providing Cache-Aside caching for active subscriptions in Redis.
 * Includes graceful degradation with automatic fallback to PostgreSQL if Redis is unavailable.
 */
@Slf4j
@Service
public class SubscriptionCacheService {

    public static final String CACHE_KEY_PREFIX = "lodestar:subscriptions:region:";
    private static final TypeReference<List<SubscriptionResponse>> SUBSCRIPTION_LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AlertSubscriptionRepository subscriptionRepository;
    private final Duration cacheTtl;

    public SubscriptionCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AlertSubscriptionRepository subscriptionRepository,
            @Value("${lodestar.cache.subscriptions-ttl:1h}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.cacheTtl = cacheTtl;
    }

    /**
     * Retrieves active subscriptions for the given region using the Cache-Aside pattern.
     * If Redis is unreachable, gracefully falls back to direct database retrieval.
     */
    public List<SubscriptionResponse> getActiveSubscriptions(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return Collections.emptyList();
        }

        String cacheKey = buildCacheKey(regionId);

        // 1. Try reading from Redis cache
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.debug("Cache HIT for region [{}]", regionId);
                return objectMapper.readValue(cachedJson, SUBSCRIPTION_LIST_TYPE);
            }
            log.debug("Cache MISS for region [{}]", regionId);
        } catch (Exception ex) {
            log.warn("Failed to read from Redis cache for region [{}]. Degrading gracefully to DB: {}",
                    regionId, ex.getMessage());
            return fetchFromDatabase(regionId);
        }

        // 2. Cache miss: fetch from PostgreSQL
        List<SubscriptionResponse> subscriptions = fetchFromDatabase(regionId);

        // 3. Populate Redis cache
        try {
            String jsonToCache = objectMapper.writeValueAsString(subscriptions);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, cacheTtl);
            log.debug("Populated Redis cache for region [{}] with TTL {}", regionId, cacheTtl);
        } catch (Exception ex) {
            log.warn("Failed to populate Redis cache for region [{}]: {}", regionId, ex.getMessage());
        }

        return subscriptions;
    }

    /**
     * Evicts cached subscriptions for a specific region.
     */
    public void evictRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }

        String cacheKey = buildCacheKey(regionId);
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.debug("Evicted cache for region [{}] (deleted: {})", regionId, deleted);
        } catch (Exception ex) {
            log.warn("Failed to evict Redis cache for region [{}]: {}", regionId, ex.getMessage());
        }
    }

    /**
     * Evicts all cached region subscriptions.
     */
    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted all subscription cache keys: {}", keys.size());
            }
        } catch (Exception ex) {
            log.warn("Failed to evict all subscription cache keys: {}", ex.getMessage());
        }
    }

    private List<SubscriptionResponse> fetchFromDatabase(String regionId) {
        List<AlertSubscription> entities = subscriptionRepository.findByRegionIdAndActiveTrue(regionId);
        return entities.stream()
                .map(SubscriptionResponse::fromEntity)
                .toList();
    }

    private String buildCacheKey(String regionId) {
        return CACHE_KEY_PREFIX + regionId;
    }
}

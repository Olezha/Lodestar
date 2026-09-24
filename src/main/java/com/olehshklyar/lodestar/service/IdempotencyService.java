package com.olehshklyar.lodestar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service providing distributed idempotency protection using Redis SETNX (setIfAbsent).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String DEDUP_PREFIX = "dedup:notification:";
    private final StringRedisTemplate redisTemplate;

    /**
     * Attempts to acquire an idempotency lock for the given taskId.
     *
     * @param taskId the unique notification task ID
     * @param ttl    time-to-live for the idempotency key
     * @return true if the task was not processed yet (lock acquired), false if duplicate
     */
    public boolean acquireLock(String taskId, Duration ttl) {
        String key = DEDUP_PREFIX + taskId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSED", ttl);
        return Boolean.TRUE.equals(isNew);
    }
}

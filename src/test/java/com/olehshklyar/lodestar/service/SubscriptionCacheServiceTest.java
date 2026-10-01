package com.olehshklyar.lodestar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AlertSubscriptionRepository subscriptionRepository;

    private ObjectMapper objectMapper;
    private SubscriptionCacheService subscriptionCacheService;

    private static final String REGION_ID = "KYIV_REGION";
    private static final String CACHE_KEY = "lodestar:subscriptions:region:KYIV_REGION";
    private static final Duration TTL = Duration.ofHours(1);

    private AlertSubscription sampleEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        subscriptionCacheService = new SubscriptionCacheService(
                redisTemplate,
                objectMapper,
                subscriptionRepository,
                TTL
        );

        sampleEntity = AlertSubscription.builder()
                .id(1L)
                .userId("user-100")
                .channel(NotificationChannel.DISCORD)
                .recipientAddress("https://discord.com/api/webhooks/100/token")
                .regionId(REGION_ID)
                .minSeverity("INFO")
                .active(true)
                .createdAt(Instant.parse("2026-10-01T12:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("Should return subscriptions from Redis on cache HIT without querying DB")
    void shouldReturnFromCacheOnHit() throws JsonProcessingException {
        // Arrange
        List<SubscriptionResponse> cachedList = List.of(SubscriptionResponse.fromEntity(sampleEntity));
        String json = objectMapper.writeValueAsString(cachedList);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(json);

        // Act
        List<SubscriptionResponse> result = subscriptionCacheService.getActiveSubscriptions(REGION_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-100");
        assertThat(result.get(0).channel()).isEqualTo(NotificationChannel.DISCORD);
        verify(subscriptionRepository, never()).findByRegionIdAndActiveTrue(anyString());
    }

    @Test
    @DisplayName("Should query database, populate Redis cache, and return subscriptions on cache MISS")
    void shouldQueryDbAndPopulateCacheOnMiss() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(subscriptionRepository.findByRegionIdAndActiveTrue(REGION_ID)).thenReturn(List.of(sampleEntity));

        // Act
        List<SubscriptionResponse> result = subscriptionCacheService.getActiveSubscriptions(REGION_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-100");
        verify(subscriptionRepository).findByRegionIdAndActiveTrue(REGION_ID);
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("Should degrade gracefully to database when Redis read throws an exception")
    void shouldDegradeToDatabaseOnRedisReadFailure() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("Redis connection timed out"));
        when(subscriptionRepository.findByRegionIdAndActiveTrue(REGION_ID)).thenReturn(List.of(sampleEntity));

        // Act
        List<SubscriptionResponse> result = subscriptionCacheService.getActiveSubscriptions(REGION_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-100");
        verify(subscriptionRepository).findByRegionIdAndActiveTrue(REGION_ID);
    }

    @Test
    @DisplayName("Should return database results even if Redis write fails on cache miss")
    void shouldReturnDbResultsWhenRedisWriteFails() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(subscriptionRepository.findByRegionIdAndActiveTrue(REGION_ID)).thenReturn(List.of(sampleEntity));
        doThrow(new RedisConnectionFailureException("Redis write failed"))
                .when(valueOperations).set(eq(CACHE_KEY), anyString(), eq(TTL));

        // Act
        List<SubscriptionResponse> result = subscriptionCacheService.getActiveSubscriptions(REGION_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-100");
        verify(subscriptionRepository).findByRegionIdAndActiveTrue(REGION_ID);
    }

    @Test
    @DisplayName("Should return empty list for null or blank regionId")
    void shouldReturnEmptyForNullOrBlankRegionId() {
        assertThat(subscriptionCacheService.getActiveSubscriptions(null)).isEmpty();
        assertThat(subscriptionCacheService.getActiveSubscriptions("   ")).isEmpty();
        verify(subscriptionRepository, never()).findByRegionIdAndActiveTrue(anyString());
    }

    @Test
    @DisplayName("Should delete region cache key on evictRegion")
    void shouldEvictRegion() {
        // Act
        subscriptionCacheService.evictRegion(REGION_ID);

        // Assert
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Should silently handle Redis exception during evictRegion")
    void shouldSilentlyHandleExceptionOnEvictRegion() {
        // Arrange
        doThrow(new RedisConnectionFailureException("Redis down")).when(redisTemplate).delete(CACHE_KEY);

        // Act & Assert - should not throw
        subscriptionCacheService.evictRegion(REGION_ID);
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Should delete all region keys on evictAll")
    void shouldEvictAll() {
        // Arrange
        Set<String> keys = Set.of(CACHE_KEY, "lodestar:subscriptions:region:LVIV_REGION");
        when(redisTemplate.keys("lodestar:subscriptions:region:*")).thenReturn(keys);

        // Act
        subscriptionCacheService.evictAll();

        // Assert
        verify(redisTemplate).delete(keys);
    }
}

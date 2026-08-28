package com.example.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisQueryEmbeddingCacheTest {
    StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    Clock clock = mock(Clock.class);

    RedisQueryEmbeddingCache cache() {
        when(template.opsForValue()).thenReturn(values);
        when(clock.millis()).thenReturn(1000L);
        return new RedisQueryEmbeddingCache(template, clock);
    }

    @Test
    void keysSeparateModelDimensionsAndQueryWithoutIncludingPlainQuery() {
        String key = RedisQueryEmbeddingCache.key("model", 1536, " space journey ");
        assertThat(key).startsWith("film-archive:query-embedding:v1:model:1536:")
                .doesNotContain("space journey")
                .isEqualTo(RedisQueryEmbeddingCache.key("model", 1536, "space journey"))
                .isNotEqualTo(RedisQueryEmbeddingCache.key("other-model", 1536, "space journey"))
                .isNotEqualTo(RedisQueryEmbeddingCache.key("model", 768, "space journey"))
                .isNotEqualTo(RedisQueryEmbeddingCache.key("model", 1536, "Space journey"));
    }

    @Test
    void writesWithAtomicTtlAndPreservesFloatValuesAndAbsoluteExpiry() {
        var cache = cache();
        var encoded = ArgumentCaptor.forClass(String.class);
        cache.put("key", new float[]{0.12345678f, -0.9f}, 61000L);
        verify(values).set(eq("key"), encoded.capture(), eq(Duration.ofSeconds(60)));
        when(values.get("key")).thenReturn(encoded.getValue());
        var hit = cache.find("key", 2).orElseThrow();
        assertThat(hit.vector()).containsExactly(0.12345678f, -0.9f);
        assertThat(hit.expiresAt()).isEqualTo(61000L);
        hit.vector()[0] = 99;
        assertThat(cache.find("key", 2).orElseThrow().vector()[0]).isEqualTo(0.12345678f);
        assertThat(cache.stats().writes()).isEqualTo(1);
        when(clock.millis()).thenReturn(61001L);
        assertThat(cache.find("key", 2)).isEmpty();
    }

    @Test
    void corruptWrongDimensionNonFiniteZeroAndExpiredEntriesAreMisses() {
        var cache = cache();
        for (String value : new String[]{"garbage", "!".repeat(24), payload(61000, 1),
                payload(61000, Float.NaN, 0), payload(61000, Float.POSITIVE_INFINITY, 1),
                payload(61000, 0, 0), payload(999, 1, 0)}) {
            when(values.get("key")).thenReturn(value);
            assertThat(cache.find("key", 2)).isEmpty();
        }
        assertThat(cache.stats().failures()).isZero();
    }

    @Test
    void failureBypassesReadsAndWritesForThirtySecondsThenRecovers() {
        var cache = cache();
        when(values.get("key")).thenThrow(new RedisConnectionFailureException("offline"))
                .thenReturn(payload(61000, 1, 0));
        assertThat(cache.find("key", 2)).isEmpty();
        assertThat(cache.find("key", 2)).isEmpty();
        cache.put("key", new float[]{1, 0}, 61000);
        verify(values, times(1)).get("key");
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        when(clock.millis()).thenReturn(31001L);
        assertThat(cache.find("key", 2)).isPresent();
        assertThat(cache.stats().failures()).isEqualTo(1);
    }

    @Test
    void writeFailureIsBestEffortAndInvalidVectorsAreNeverWritten() {
        var cache = cache();
        cache.put("empty", null, 61000);
        cache.put("zero", new float[]{0, 0}, 61000);
        cache.put("invalid", new float[]{Float.NaN, 0}, 61000);
        cache.put("expired", new float[]{1, 0}, 999);
        verifyNoInteractions(values);
        doThrow(new RedisConnectionFailureException("offline")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        cache.put("key", new float[]{1, 0}, 61000);
        assertThat(cache.stats().failures()).isEqualTo(1);
        assertThat(cache.stats().writes()).isZero();
    }

    @Test
    void cacheIsOptInAndDisabledModeDoesNotNeedRedisInfrastructure() {
        var context = new ApplicationContextRunner().withUserConfiguration(RedisQueryEmbeddingCache.class);
        context.run(app -> assertThat(app).hasNotFailed().doesNotHaveBean(RedisQueryEmbeddingCache.class));
        context.withPropertyValues("archive.search.redis-enabled=false")
                .run(app -> assertThat(app).hasNotFailed().doesNotHaveBean(RedisQueryEmbeddingCache.class));
        context.withPropertyValues("archive.search.redis-enabled=true")
                .withBean(StringRedisTemplate.class, () -> template)
                .run(app -> assertThat(app).hasNotFailed().hasSingleBean(RedisQueryEmbeddingCache.class));
        verify(template, never()).opsForValue();
    }

    private static String payload(long expiresAt, float... vector) {
        var buffer = ByteBuffer.allocate(Long.BYTES + vector.length * Float.BYTES).putLong(expiresAt);
        for (float value : vector) buffer.putFloat(value);
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}

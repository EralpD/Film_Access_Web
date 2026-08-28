package com.example.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ArchiveQueryEmbeddingServiceTest {
    FilmEmbeddingService model = mock(FilmEmbeddingService.class);

    @Test
    void redisHitPopulatesRamWithoutCallingProviderOrExtendingSharedExpiry() {
        var redis = mock(RedisQueryEmbeddingCache.class);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        when(redis.find(anyString(), eq(2))).thenReturn(java.util.Optional.of(
                new RedisQueryEmbeddingCache.Entry(new float[]{1, 0}, 1100L)), java.util.Optional.empty());
        when(model.createQueryEmbedding("space")).thenReturn(new float[]{0, 1});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2, clock, redis);
        try {
            service.find("space").orElseThrow()[0] = 99;
            assertThat(service.find("space").orElseThrow()).containsExactly(1f, 0f);
            verify(redis, times(1)).find(anyString(), eq(2));
            verify(model, never()).createQueryEmbedding(anyString());
            when(clock.millis()).thenReturn(1101L);
            assertThat(service.find("space").orElseThrow()).containsExactly(0f, 1f);
            verify(redis, timeout(1000)).put(anyString(), any(float[].class), eq(61101L));
        } finally { service.close(); }
    }

    @Test
    void redisOutageDoesNotOpenProviderCooldownAndRamStillWorks() {
        var template = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        var redis = new RedisQueryEmbeddingCache(template);
        when(model.createQueryEmbedding(anyString())).thenReturn(new float[]{1, 0});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2, Clock.systemUTC(), redis);
        try {
            assertThat(service.find("space")).isPresent();
            assertThat(service.find("space")).isPresent();
            assertThat(service.find("dreams")).isPresent();
            verify(model, times(2)).createQueryEmbedding(anyString());
            verify(template, times(1)).opsForValue();
            assertThat(redis.stats().failures()).isEqualTo(1);
        } finally { service.close(); }
    }

    @Test
    void redisHitsAreAvailableDuringProviderCooldown() {
        var redis = mock(RedisQueryEmbeddingCache.class);
        when(redis.find(anyString(), eq(2))).thenReturn(java.util.Optional.empty(), java.util.Optional.of(
                new RedisQueryEmbeddingCache.Entry(new float[]{1, 0}, System.currentTimeMillis() + 60000)));
        when(model.createQueryEmbedding("new query")).thenThrow(new IllegalStateException("provider offline"));
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2, Clock.systemUTC(), redis);
        try {
            assertThat(service.find("new query")).isEmpty();
            assertThat(service.find("shared cached query")).isPresent();
            verify(model, times(1)).createQueryEmbedding(anyString());
            verify(redis, never()).put(anyString(), any(float[].class), anyLong());
        } finally { service.close(); }
    }

    @Test
    void searchDoesNotWaitForRedisWriteAndWriteFailureDoesNotInvalidateResult() throws Exception {
        var redis = mock(RedisQueryEmbeddingCache.class);
        var writeStarted = new java.util.concurrent.CountDownLatch(1);
        var releaseWrite = new java.util.concurrent.CountDownLatch(1);
        when(model.createQueryEmbedding(anyString())).thenReturn(new float[]{1, 0});
        doAnswer(call -> {
            writeStarted.countDown();
            releaseWrite.await(2, java.util.concurrent.TimeUnit.SECONDS);
            throw new IllegalStateException("write failed");
        }).when(redis).put(anyString(), any(float[].class), anyLong());
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2, Clock.systemUTC(), redis);
        try {
            assertThat(service.find("space")).isPresent();
            assertThat(writeStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(service.find("space")).isPresent();
            releaseWrite.countDown();
            assertThat(service.find("dreams")).isPresent();
        } finally { releaseWrite.countDown(); service.close(); }
    }

    @Test
    void concurrentIdenticalQueriesShareOneProviderCallAndIndependentVectors() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(model.createQueryEmbedding("dreams")).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException();
            return new float[]{1, 0};
        });
        var redis = mock(RedisQueryEmbeddingCache.class);
        var service = new ArchiveQueryEmbeddingService(model, 4000, 60000, 8, 2, Clock.systemUTC(), redis);
        try (var callers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<java.util.Optional<float[]>>>();
            for (int i = 0; i < 8; i++) futures.add(callers.submit(() -> service.find("dreams")));
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (service.stats().sharedWaits() < 7 && System.nanoTime() < deadline) Thread.sleep(5);
            release.countDown();
            for (var future : futures) assertThat(future.get(2, java.util.concurrent.TimeUnit.SECONDS).orElseThrow()).containsExactly(1f, 0f);
            futures.getFirst().get().orElseThrow()[0] = 99;
            assertThat(service.find("dreams").orElseThrow()).containsExactly(1f, 0f);
            assertThat(service.stats().sharedWaits()).isEqualTo(7);
            verify(model, times(1)).createQueryEmbedding("dreams");
            verify(redis, times(1)).find(anyString(), eq(2));
            verify(redis, timeout(1000).times(1)).put(anyString(), any(float[].class), anyLong());
        } finally { release.countDown(); service.close(); }
    }

    @Test
    void interruptedWaiterDoesNotCancelAnotherWaiter() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(model.createQueryEmbedding("dreams")).thenAnswer(invocation -> {
            entered.countDown(); release.await(3, java.util.concurrent.TimeUnit.SECONDS); return new float[]{1, 0};
        });
        var service = new ArchiveQueryEmbeddingService(model, 4000, 60000, 8, 2);
        try (var callers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> service.find("dreams"));
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = callers.submit(() -> service.find("dreams"));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (service.stats().sharedWaits() < 1 && System.nanoTime() < deadline) Thread.sleep(5);
            first.cancel(true); release.countDown();
            assertThat(second.get(2, java.util.concurrent.TimeUnit.SECONDS)).isPresent();
            verify(model, times(1)).createQueryEmbedding("dreams");
        } finally { release.countDown(); service.close(); }
    }

    @Test
    void cacheReusesQueryAndDoesNotExposeMutableVector() {
        when(model.getEmbeddingModelName()).thenReturn("test");
        when(model.createQueryEmbedding("space")).thenReturn(new float[]{1, 0});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2);
        try {
            service.find(" space ").orElseThrow()[0] = 99;
            assertThat(service.find("space").orElseThrow()).containsExactly(1f, 0f);
            verify(model, times(1)).createQueryEmbedding("space");
        } finally { service.close(); }
    }

    @Test
    void apiFailureOpensCooldownButDoesNotCacheFailures() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        when(model.createQueryEmbedding("space")).thenThrow(new IllegalStateException())
                .thenReturn(new float[]{1, 0});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2, clock);
        try {
            assertThat(service.find("space")).isEmpty();
            assertThat(service.find("space")).isEmpty();
            verify(model, times(1)).createQueryEmbedding("space");
            when(clock.millis()).thenReturn(32000L);
            assertThat(service.find("space")).isPresent();
        } finally { service.close(); }
    }

    @Test
    void timeoutReturnsWithoutWaitingForProvider() {
        when(model.createQueryEmbedding("slow")).thenAnswer(invocation -> {
            Thread.sleep(5000);
            return new float[]{1, 0};
        });
        var service = new ArchiveQueryEmbeddingService(model, 50, 60000, 2, 2);
        try {
            org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(1),
                    () -> assertThat(service.find("slow")).isEmpty());
        } finally { service.close(); }
    }

    @Test
    void wrongDimensionsAndNonFiniteValuesAreRejected() {
        when(model.createQueryEmbedding("bad")).thenReturn(new float[]{Float.NaN, 0});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2);
        try { assertThat(service.find("bad")).isEmpty(); } finally { service.close(); }
        when(model.createQueryEmbedding("bad")).thenReturn(new float[]{1});
        service = new ArchiveQueryEmbeddingService(model, 1000, 60000, 2, 2);
        try { assertThat(service.find("bad")).isEmpty(); } finally { service.close(); }
    }

    @Test
    void cacheExpiresAndEvictsOldEntries() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        when(model.createQueryEmbedding(anyString())).thenReturn(new float[]{1, 0});
        var service = new ArchiveQueryEmbeddingService(model, 1000, 100, 1, 2, clock);
        try {
            service.find("a"); service.find("b"); service.find("a");
            verify(model, times(2)).createQueryEmbedding("a");
            when(clock.millis()).thenReturn(1200L);
            service.find("a");
            verify(model, times(3)).createQueryEmbedding("a");
        } finally { service.close(); }
    }
}

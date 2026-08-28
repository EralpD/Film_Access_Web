package com.example.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Redis and Boot connection configuration; no production Redis or embedding API is used. */
@Testcontainers
class RedisQueryEmbeddingCacheIntegrationTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru",
                    "--save", "", "--appendonly", "no");

    private ApplicationContextRunner context(int port, FilmEmbeddingService model) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withUserConfiguration(RedisQueryEmbeddingCache.class, ArchiveQueryEmbeddingService.class)
                .withBean(FilmEmbeddingService.class, () -> model)
                .withPropertyValues("archive.search.redis-enabled=true",
                        "spring.ai.openai.embedding.options.dimensions=2", "archive.search.cache-ttl-ms=60000",
                        "spring.data.redis.url=redis://" + redis.getHost() + ":" + port,
                        "spring.data.redis.connect-timeout=500ms", "spring.data.redis.timeout=500ms",
                        "spring.data.redis.repositories.enabled=false", "spring.data.redis.lettuce.pool.enabled=false");
    }

    @Test
    void separateApplicationInstancesShareVectorsAndRedisExpiresKeys() {
        var model = mock(FilmEmbeddingService.class);
        when(model.getEmbeddingModelName()).thenReturn("integration-model");
        when(model.createQueryEmbedding("time inversion")).thenReturn(new float[]{1, 0});
        context(redis.getMappedPort(6379), model).run(app -> {
            assertThat(app).hasNotFailed();
            var cache = app.getBean(RedisQueryEmbeddingCache.class);
            var template = app.getBean(StringRedisTemplate.class);
            var first = app.getBean(ArchiveQueryEmbeddingService.class);
            try {
                assertThat(first.find("time inversion")).isPresent();
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(() -> assertThat(cache.stats().writes()).isEqualTo(1));
            } finally { first.close(); }

            String key = RedisQueryEmbeddingCache.key("integration-model", 2, "time inversion");
            assertThat(template.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
            // A second Spring context uses a different connection and has an empty RAM cache.
            context(redis.getMappedPort(6379), model).run(secondApp -> {
                assertThat(secondApp).hasNotFailed();
                var secondCache = secondApp.getBean(RedisQueryEmbeddingCache.class);
                var second = secondApp.getBean(ArchiveQueryEmbeddingService.class);
                try {
                    assertThat(second.find("time inversion").orElseThrow()).containsExactly(1f, 0f);
                    assertThat(secondCache.stats().hits()).isEqualTo(1);
                    verify(model, times(1)).createQueryEmbedding("time inversion");
                } finally { second.close(); }
            });

            cache.put("short-lived-test", new float[]{1, 0}, System.currentTimeMillis() + 2000);
            assertThat(cache.stats().writes()).isEqualTo(2);
            assertThat(template.getExpire("short-lived-test", TimeUnit.MILLISECONDS)).isBetween(1L, 2000L);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(template.hasKey("short-lived-test")).isFalse());
        });
    }

    @Test
    void unreachableRedisDoesNotPreventStartupOrSuccessfulEmbeddingAndRamReuse() {
        // Reserve a closed port on the container's host without changing the running Redis service.
        try (var socket = new java.net.ServerSocket(0)) {
            int closedPort = socket.getLocalPort();
            socket.close();
            var model = mock(FilmEmbeddingService.class);
            when(model.createQueryEmbedding(anyString())).thenReturn(new float[]{1, 0});
            context(closedPort, model).run(app -> {
                assertThat(app).hasNotFailed();
                var cache = app.getBean(RedisQueryEmbeddingCache.class);
                var service = app.getBean(ArchiveQueryEmbeddingService.class);
                try {
                    org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
                        assertThat(service.find("offline redis query")).isPresent();
                        assertThat(service.find("offline redis query")).isPresent();
                        assertThat(service.find("another query")).isPresent();
                    });
                    verify(model, times(2)).createQueryEmbedding(anyString());
                    assertThat(cache.stats().failures()).isEqualTo(1);
                } finally { service.close(); }
            });
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    @Test
    void unresponsiveRedisTimesOutWithoutBreakingSearch() {
        var model = mock(FilmEmbeddingService.class);
        when(model.createQueryEmbedding(anyString())).thenReturn(new float[]{1, 0});
        context(redis.getMappedPort(6379), model).run(app -> {
            var cache = app.getBean(RedisQueryEmbeddingCache.class);
            var template = app.getBean(StringRedisTemplate.class);
            // Establish the connection, then stop server responses without closing its TCP port.
            template.hasKey("warm-connection");
            template.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                    connection.execute("CLIENT", "PAUSE".getBytes(StandardCharsets.US_ASCII),
                            "1500".getBytes(StandardCharsets.US_ASCII), "ALL".getBytes(StandardCharsets.US_ASCII)));
            var service = app.getBean(ArchiveQueryEmbeddingService.class);
            org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(3),
                    () -> assertThat(service.find("slow redis query")).isPresent());
            assertThat(cache.stats().failures()).isEqualTo(1);
            assertThat(service.find("slow redis query")).isPresent();
            verify(model, times(1)).createQueryEmbedding(anyString());
        });
        // Keep the following test independent of the temporary server pause.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                redis.execInContainer("redis-cli", "PING").getStdout().trim().equals("PONG"));
    }
}

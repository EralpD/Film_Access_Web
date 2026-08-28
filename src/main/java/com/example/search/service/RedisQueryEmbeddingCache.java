package com.example.search.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Optional, best-effort L2 cache. Never stores film lists, user data, or failed embeddings. */
@Component
@ConditionalOnProperty(name = "archive.search.redis-enabled", havingValue = "true")
public class RedisQueryEmbeddingCache {
    private static final Logger log = LoggerFactory.getLogger(RedisQueryEmbeddingCache.class);
    private static final long FAILURE_COOLDOWN_MS = 30_000;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AtomicLong retryAfter = new AtomicLong();
    private final LongAdder hits = new LongAdder();
    private final LongAdder writes = new LongAdder();
    private final LongAdder failures = new LongAdder();

    @org.springframework.beans.factory.annotation.Autowired
    public RedisQueryEmbeddingCache(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    RedisQueryEmbeddingCache(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    // Version both the query preparation and payload format. Only trim: do not merge distinct queries.
    static String key(String model, int dimensions, String query) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(query.trim().getBytes(StandardCharsets.UTF_8));
            return "film-archive:query-embedding:v1:" + model + ":" + dimensions + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    public Optional<Entry> find(String key, int dimensions) {
        if (clock.millis() < retryAfter.get()) return Optional.empty();
        try {
            String encoded = redis.opsForValue().get(key);
            if (encoded == null) return Optional.empty();
            // Explicit binary format, not Java object deserialization. Check size before decoding.
            int bytes = Long.BYTES + dimensions * Float.BYTES;
            if (encoded.length() != 4 * ((bytes + 2) / 3)) return Optional.empty();
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            if (buffer.remaining() != bytes) return Optional.empty();
            long expiresAt = buffer.getLong();
            if (expiresAt <= clock.millis()) return Optional.empty();
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) vector[i] = buffer.getFloat();
            if (!valid(vector)) return Optional.empty();
            hits.increment();
            return Optional.of(new Entry(vector, expiresAt));
        } catch (IllegalArgumentException corruptPayload) {
            return Optional.empty();
        } catch (RuntimeException unavailable) {
            failed(unavailable);
            return Optional.empty();
        }
    }

    public void put(String key, float[] vector, long expiresAt) {
        long remaining = expiresAt - clock.millis();
        if (remaining <= 0 || clock.millis() < retryAfter.get() || !valid(vector)) return;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + vector.length * Float.BYTES);
            buffer.putLong(expiresAt);
            for (float value : vector) buffer.putFloat(value);
            // One atomic SET with expiry; a crash cannot leave a permanent cache key.
            redis.opsForValue().set(key, Base64.getEncoder().encodeToString(buffer.array()), Duration.ofMillis(remaining));
            writes.increment();
        } catch (RuntimeException unavailable) {
            failed(unavailable);
        }
    }

    private static boolean valid(float[] vector) {
        if (vector == null || vector.length == 0) return false;
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) return false;
            norm += (double) value * value;
        }
        return norm > 0;
    }

    private void failed(RuntimeException failure) {
        failures.increment();
        long now = clock.millis();
        long previous = retryAfter.getAndAccumulate(now + FAILURE_COOLDOWN_MS, Math::max);
        // Do not log queries, values, connection strings, credentials, or exception messages.
        if (previous <= now) log.warn("Query embedding Redis cache bypassed for 30 seconds ({})",
                failure.getClass().getSimpleName());
    }

    public Stats stats() { return new Stats(hits.sum(), writes.sum(), failures.sum()); }
    public record Stats(long hits, long writes, long failures) {}
    public record Entry(float[] vector, long expiresAt) {}
}

package com.example.search.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

/** Bounded latency/concurrency and a bounded success-only cache for archive queries. */
@Service
public class ArchiveQueryEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(ArchiveQueryEmbeddingService.class);
    private final FilmEmbeddingService embeddings;
    private final long timeoutMillis;
    private final long ttlMillis;
    private final int maxEntries;
    private final int dimensions;
    private final Clock clock;
    private final RedisQueryEmbeddingCache redisCache;
    private final AtomicLong retryAfter = new AtomicLong();
    private final LinkedHashMap<String, CachedVector> cache = new LinkedHashMap<>(16, .75f, true);
    // Guarded by cache, including publication of newly submitted work.
    private final Map<String, Flight> inFlight = new HashMap<>();
    private final java.util.concurrent.atomic.LongAdder cacheHits = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder sharedWaits = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder providerCalls = new java.util.concurrent.atomic.LongAdder();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), Thread.ofPlatform().daemon().name("archive-embedding-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @org.springframework.beans.factory.annotation.Autowired
    public ArchiveQueryEmbeddingService(FilmEmbeddingService embeddings,
            @Value("${archive.search.embedding-timeout-ms:4000}") long timeoutMillis,
            @Value("${archive.search.cache-ttl-ms:900000}") long ttlMillis,
            @Value("${archive.search.cache-size:512}") int maxEntries,
            @Value("${spring.ai.openai.embedding.options.dimensions:1536}") int dimensions,
            Optional<RedisQueryEmbeddingCache> redisCache) {
        this(embeddings, timeoutMillis, ttlMillis, maxEntries, dimensions, Clock.systemUTC(), redisCache.orElse(null));
    }

    public ArchiveQueryEmbeddingService(FilmEmbeddingService embeddings, long timeoutMillis, long ttlMillis,
            int maxEntries, int dimensions) {
        this(embeddings, timeoutMillis, ttlMillis, maxEntries, dimensions, Clock.systemUTC(), null);
    }

    ArchiveQueryEmbeddingService(FilmEmbeddingService embeddings, long timeoutMillis, long ttlMillis,
            int maxEntries, int dimensions, Clock clock) {
        this(embeddings, timeoutMillis, ttlMillis, maxEntries, dimensions, clock, null);
    }

    ArchiveQueryEmbeddingService(FilmEmbeddingService embeddings, long timeoutMillis, long ttlMillis,
            int maxEntries, int dimensions, Clock clock, RedisQueryEmbeddingCache redisCache) {
        if (timeoutMillis < 1 || ttlMillis < 1 || maxEntries < 1 || dimensions < 1)
            throw new IllegalArgumentException("Embedding cache and timeout settings must be positive.");
        this.embeddings = embeddings;
        this.timeoutMillis = timeoutMillis;
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.dimensions = dimensions;
        this.clock = clock;
        this.redisCache = redisCache;
    }

    public Optional<float[]> find(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String normalized = query.trim();
        String key = RedisQueryEmbeddingCache.key(embeddings.getEmbeddingModelName(), dimensions, normalized);
        Flight flight;
        synchronized (cache) {
            CachedVector cached = cache.get(key);
            if (cached != null && cached.expiresAt() > clock.millis()) {
                cacheHits.increment();
                return Optional.of(cached.vector().clone());
            }
            cache.remove(key);
            flight = inFlight.get(key);
            if (flight == null) {
                flight = new Flight(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
                inFlight.put(key, flight);
                Flight submitted = flight;
                try { flight.task = executor.submit(() -> generate(key, normalized, submitted)); }
                catch (RuntimeException failure) { fail(key, flight, failure); }
            } else sharedWaits.increment();
        }
        try {
            float[] vector = flight.result.get(Math.max(1, flight.deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return vector == null ? Optional.empty() : Optional.of(vector.clone());
        } catch (InterruptedException interrupted) {
            // A disconnected/interrupted caller must not cancel work shared by other requests.
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException timeout) {
            fail(key, flight, timeout);
            if (flight.task != null) flight.task.cancel(true);
            executor.purge();
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException failed) {
            return Optional.empty();
        }
    }

    private void generate(String key, String query, Flight flight) {
        try {
            // All I/O stays outside the RAM-cache monitor and inside the existing bounded worker pool.
            if (redisCache != null) {
                Optional<RedisQueryEmbeddingCache.Entry> shared = redisCache.find(key, dimensions);
                if (shared.isPresent()) {
                    var entry = shared.get();
                    complete(key, flight, entry.vector(), Math.min(entry.expiresAt(), clock.millis() + ttlMillis));
                    return;
                }
            }
            synchronized (cache) {
                if (inFlight.get(key) != flight || flight.result.isDone()) return;
                // Redis hits remain usable even while the embedding provider is in cooldown.
                if (clock.millis() < retryAfter.get()) {
                    inFlight.remove(key, flight);
                    flight.result.complete(null);
                    return;
                }
            }
            providerCalls.increment();
            float[] vector = embeddings.createQueryEmbedding(query);
            validate(vector);
            long expiresAt = clock.millis() + ttlMillis;
            // Release waiting searches before a best-effort Redis write. No extra executor or queue.
            if (complete(key, flight, vector, expiresAt) && redisCache != null)
                redisCache.put(key, vector, expiresAt);
        } catch (Exception exception) { fail(key, flight, exception); }
    }

    private boolean complete(String key, Flight flight, float[] vector, long expiresAt) {
        synchronized (cache) {
            if (inFlight.get(key) != flight || flight.result.isDone()) return false;
            cache.put(key, new CachedVector(vector.clone(), expiresAt));
            while (cache.size() > maxEntries) cache.remove(cache.keySet().iterator().next());
            inFlight.remove(key);
            flight.result.complete(vector.clone());
            return true;
        }
    }

    private void fail(String key, Flight flight, Exception failure) {
        synchronized (cache) {
            if (!flight.result.completeExceptionally(failure)) return;
            inFlight.remove(key, flight);
            retryAfter.set(clock.millis() + 30_000);
        }
        log.warn("Archive semantic search temporarily unavailable ({})", failure.getClass().getSimpleName());
    }

    public Stats stats() { return new Stats(cacheHits.sum(), sharedWaits.sum(), providerCalls.sum()); }
    public record Stats(long cacheHits, long sharedWaits, long providerCalls) {}
    private static final class Flight {
        final CompletableFuture<float[]> result = new CompletableFuture<>();
        final long deadline;
        Future<?> task;
        Flight(long deadline) { this.deadline = deadline; }
    }

    private void validate(float[] vector) {
        if (vector == null || vector.length != dimensions)
            throw new IllegalStateException("Unexpected embedding dimensions.");
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite embedding.");
            norm += (double) value * value;
        }
        if (norm == 0) throw new IllegalStateException("Zero embedding.");
    }

    @PreDestroy
    public void close() {
        synchronized (cache) {
            inFlight.values().forEach(f -> f.result.completeExceptionally(new IllegalStateException("Service stopped")));
            inFlight.clear();
        }
        executor.shutdownNow();
    }

    private record CachedVector(float[] vector, long expiresAt) {}
}

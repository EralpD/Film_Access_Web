package com.example.search.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.film.Film;
import com.example.film.FilmRepository;

/** Repairs only saved films, with a bounded batch and persistent retry budget. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "archive.search.repair-enabled", havingValue = "true", matchIfMissing = true)
public class ArchiveEmbeddingRepairWorker {
    private static final Logger log = LoggerFactory.getLogger(ArchiveEmbeddingRepairWorker.class);
    private final JdbcTemplate jdbc;
    private final FilmRepository films;
    private final FilmSemanticIndexService indexer;
    private final FilmEmbeddingService embeddings;
    private final int batchSize;

    public ArchiveEmbeddingRepairWorker(JdbcTemplate jdbc, FilmRepository films,
            FilmSemanticIndexService indexer, FilmEmbeddingService embeddings,
            @Value("${archive.search.repair-batch-size:16}") int batchSize) {
        this.jdbc = jdbc;
        this.films = films;
        this.indexer = indexer;
        this.embeddings = embeddings;
        this.batchSize = Math.max(1, Math.min(batchSize, 64));
    }

    @Scheduled(initialDelayString = "${archive.search.repair-initial-delay-ms:60000}",
            fixedDelayString = "${archive.search.repair-delay-ms:60000}")
    public void repair() {
        // A session advisory lock keeps multiple application instances from doing duplicate work.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT pg_try_advisory_lock(734221809)")) {
                result.next();
                if (!result.getBoolean(1)) return null;
            }
            try {
                repairBatch();
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SELECT pg_advisory_unlock(734221809)");
                }
            }
            return null;
        });
    }

    void repairBatch() {
        String model = embeddings.getEmbeddingModelName();
        List<Pending> pending = jdbc.query("""
            SELECT f.id, f.search_content_hash FROM films f
            WHERE (f.embedding_requested OR EXISTS (SELECT 1 FROM user_films uf WHERE uf.film_id = f.id))
              AND NOT coalesce(f.embedding IS NOT NULL AND f.embedding_model = ?
                  AND f.embedding_content_hash = f.search_content_hash, false)
              AND (f.embedding_retry_model IS DISTINCT FROM ?
                  OR f.embedding_retry_hash IS DISTINCT FROM f.search_content_hash
                  OR (f.embedding_retry_count < 3
                      AND (f.embedding_retry_after IS NULL OR f.embedding_retry_after <= CURRENT_TIMESTAMP)))
            ORDER BY f.embedding_retry_count, f.id LIMIT ?
            """, (rs, row) -> new Pending(rs.getLong("id"), rs.getString("search_content_hash")),
                model, model, batchSize);
        if (pending.isEmpty()) return;
        List<Film> batch = films.findAllById(pending.stream().map(Pending::id).toList());
        try {
            int indexed = indexer.indexFilms(batch);
            log.info("Archive embedding repair: indexed {} films", indexed);
        } catch (RuntimeException exception) {
            for (Pending item : pending) {
                jdbc.update("""
                    UPDATE films SET
                        embedding_retry_count = CASE
                            WHEN embedding_retry_model = ? AND embedding_retry_hash = ?
                            THEN embedding_retry_count + 1 ELSE 1 END,
                        embedding_retry_after = CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                        embedding_retry_model = ?, embedding_retry_hash = ?
                    WHERE id = ? AND search_content_hash = ?
                      AND NOT coalesce(embedding IS NOT NULL AND embedding_model = ?
                          AND embedding_content_hash = search_content_hash, false)
                    """, model, item.hash(), model, item.hash(), item.id(), item.hash(), model);
            }
            log.warn("Archive embedding repair postponed for {} films ({})",
                    pending.size(), exception.getClass().getSimpleName());
        }
    }

    private record Pending(long id, String hash) {}
}

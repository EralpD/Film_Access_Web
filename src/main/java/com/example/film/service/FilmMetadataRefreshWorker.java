package com.example.film.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.catalog.metadata-refresh-enabled", havingValue = "true", matchIfMissing = true)
public class FilmMetadataRefreshWorker {
    private final JdbcTemplate jdbc;
    private final FilmCatalogDiscoveryService discovery;
    private final int batchSize;
    public FilmMetadataRefreshWorker(JdbcTemplate jdbc, FilmCatalogDiscoveryService discovery,
            @Value("${app.catalog.metadata-refresh-batch-size:4}") int batchSize) {
        this.jdbc = jdbc; this.discovery = discovery;
        this.batchSize = Math.max(1, Math.min(batchSize, 16));
    }
    @Scheduled(initialDelayString = "${app.catalog.metadata-refresh-initial-delay-ms:60000}",
            fixedDelayString = "${app.catalog.metadata-refresh-delay-ms:60000}")
    public void refresh() {
        for (int i = 0; i < batchSize; i++) {
            // Claim one film at a time. Leases and attempts survive restarts and prevent parallel duplicate work.
            var jobs = jdbc.query("""
                WITH next AS (
                    SELECT film_id FROM film_metadata_refresh
                    WHERE attempts < 3 AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY next_attempt_at, film_id FOR UPDATE SKIP LOCKED LIMIT 1
                ), claimed AS (
                    UPDATE film_metadata_refresh q SET attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '10 minutes'
                    FROM next WHERE q.film_id = next.film_id RETURNING q.film_id
                ) SELECT f.id, f.imdb_id FROM films f JOIN claimed c ON c.film_id = f.id
                """, (rs, row) -> new Job(rs.getLong("id"), rs.getString("imdb_id")));
            if (jobs.isEmpty()) return;
            Job job = jobs.getFirst();
            try {
                var film = discovery.refreshMetadata(job.imdbId());
                if (!FilmMaintenanceQueue.missingCredits(film))
                    jdbc.update("DELETE FROM film_metadata_refresh WHERE film_id = ?", job.id());
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Film metadata refresh postponed ({})",
                        exception.getClass().getSimpleName());
            }
        }
    }
    private record Job(long id, String imdbId) {}
}

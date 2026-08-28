package com.example.film.service;

import com.example.film.Film;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Durable work requests; no external calls on the detail-page thread. */
@Component
public class FilmMaintenanceQueue {
    private final JdbcTemplate jdbc;
    private final String model;
    public FilmMaintenanceQueue(JdbcTemplate jdbc,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {
        this.jdbc = jdbc; this.model = model;
    }
    public void request(Film film, boolean refreshMetadata) {
        jdbc.update("""
            UPDATE films SET embedding_requested = true WHERE id = ? AND NOT embedding_requested
              AND NOT coalesce(embedding IS NOT NULL AND embedding_model = ?
                AND embedding_content_hash = search_content_hash, false)
            """, film.getId(), model);
        if (refreshMetadata || missingCredits(film)) jdbc.update("""
            INSERT INTO film_metadata_refresh(film_id) VALUES (?) ON CONFLICT DO NOTHING
            """, film.getId());
    }
    public static boolean missingCredits(Film film) {
        return missing(film.getActors()) || missing(film.getDirector());
    }
    public static boolean missing(String value) {
        return value == null || value.isBlank() || "N/A".equalsIgnoreCase(value.trim());
    }
}

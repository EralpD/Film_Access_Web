package com.example.search;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FilmEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    private final VectorFormatter vectorFormatter;


    public FilmEmbeddingRepository(
            JdbcTemplate jdbcTemplate,
            VectorFormatter vectorFormatter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorFormatter = vectorFormatter;
    }


    public void updateEmbedding(
            Long filmId,
            float[] embedding,
            String embeddingModel,
            String contentHash
    ) {

        String vector =
                vectorFormatter.toPgVector(
                        embedding
                );

        jdbcTemplate.update(
                """
                UPDATE films
                SET embedding = CAST(? AS vector),
                    embedding_model = ?,
                    embedding_content_hash = ?,
                    embedding_updated_at = CURRENT_TIMESTAMP,
                    embedding_retry_count = 0,
                    embedding_retry_after = NULL,
                    embedding_retry_model = NULL,
                    embedding_retry_hash = NULL,
                    embedding_requested = false
                WHERE id = ? AND search_content_hash = ?
                """,
                vector,
                embeddingModel,
                contentHash,
                filmId,
                contentHash
        );
    }


    public boolean hasCurrentEmbedding(
            Long filmId,
            String embeddingModel,
            String contentHash
    ) {

        Boolean result =
                jdbcTemplate.queryForObject(
                        """
                        SELECT embedding IS NOT NULL
                           AND embedding_model = ?
                           AND embedding_content_hash = ?
                        FROM films
                        WHERE id = ?
                        """,
                        Boolean.class,
                        embeddingModel,
                        contentHash,
                        filmId
                );

        return Boolean.TRUE.equals(result);
    }
}

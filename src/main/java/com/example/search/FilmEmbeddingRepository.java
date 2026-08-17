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
            float[] embedding
    ) {

        String vector =
                vectorFormatter.toPgVector(
                        embedding
                );

        jdbcTemplate.update(
                """
                UPDATE films
                SET embedding = CAST(? AS vector)
                WHERE id = ?
                """,
                vector,
                filmId
        );
    }


    public boolean hasEmbedding(Long filmId) {

        Boolean result =
                jdbcTemplate.queryForObject(
                        """
                        SELECT embedding IS NOT NULL
                        FROM films
                        WHERE id = ?
                        """,
                        Boolean.class,
                        filmId
                );

        return Boolean.TRUE.equals(result);
    }
}

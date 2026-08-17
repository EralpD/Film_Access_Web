package com.example.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.archive.ArchiveSearchRequest;

@Repository
public class ArchiveSemanticSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    private final VectorFormatter vectorFormatter;


    public ArchiveSemanticSearchRepository(
            JdbcTemplate jdbcTemplate,
            VectorFormatter vectorFormatter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorFormatter = vectorFormatter;
    }


    public List<Long> search(
            Long userId,
            float[] queryEmbedding,
            ArchiveSearchRequest request
    ) {

        String vector =
                vectorFormatter.toPgVector(
                        queryEmbedding
                );

        StringBuilder sql = new StringBuilder(
                """
                SELECT uf.id
                FROM user_films uf

                JOIN films f
                    ON f.id = uf.film_id

                WHERE uf.user_id = ?
                  AND f.embedding IS NOT NULL
                """
        );

        List<Object> parameters =
                new ArrayList<>();

        parameters.add(userId);


        if (request.getYearFrom() != null) {

            sql.append(
                    """
                     AND f.release_year >= ?
                    """
            );

            parameters.add(
                    request.getYearFrom()
            );
        }


        if (request.getYearTo() != null) {

            sql.append(
                    """
                     AND f.release_year <= ?
                    """
            );

            parameters.add(
                    request.getYearTo()
            );
        }


        if (request.getType() != null) {

            sql.append(
                    """
                     AND f.type = ?
                    """
            );

            parameters.add(
                    request.getType().name()
            );
        }


        if (request.hasGenre()) {

            sql.append(
                    """
                     AND LOWER(f.genres_text)
                         LIKE LOWER(?)
                    """
            );

            parameters.add(
                    "%"
                    + request.getGenre().trim()
                    + "%"
            );
        }


        sql.append(
                """
                 ORDER BY
                    f.embedding <=> CAST(? AS vector),
                    uf.added_at DESC
                """
        );

        parameters.add(vector);


        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) ->
                        resultSet.getLong("id"),
                parameters.toArray()
        );
    }
}

package com.example.buddy.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.search.VectorFormatter;
import com.example.buddy.record.FilmIntent;

@Repository
public class BuddyRecommendationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final VectorFormatter vectorFormatter;
    private final double minSimilarity;

    public BuddyRecommendationRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            VectorFormatter vectorFormatter,

            @Value(
                "${buddy.recommendations.min-similarity:0.28}"
            )
            double minSimilarity
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorFormatter = vectorFormatter;
        this.minSimilarity = Math.max(
            -1.0,
            Math.min(minSimilarity, 1.0)
        );
    }

    public List<BuddyFilmCandidate> findCandidates(
            Long userId,
            float[] queryEmbedding,
            FilmIntent intent,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
            WITH query_parameters AS (
                SELECT CAST(:queryVector AS vector)
                    AS query_embedding
            ),

            user_profile AS (
                SELECT AVG(f.embedding) AS profile_embedding

                FROM user_films uf

                JOIN films f
                    ON f.id = uf.film_id

                WHERE uf.user_id = :userId
                  AND f.embedding IS NOT NULL
            ),

            scored AS (
                SELECT
                    f.imdb_id,
                    f.title,
                    f.year_text,
                    f.type,
                    f.genres_text,
                    f.poster_url,

                    GREATEST(
                        0.0,
                        1.0 - (
                            f.embedding
                            <=> query_parameters.query_embedding
                        )
                    ) AS request_score,

                    CASE
                        WHEN user_profile.profile_embedding IS NULL
                            THEN 0.0
                        ELSE GREATEST(
                            0.0,
                            1.0 - (
                                f.embedding
                                <=> user_profile.profile_embedding
                            )
                        )
                    END AS taste_score,

                    LEAST(
                        1.0,
                        GREATEST(
                            0.0,
                            COALESCE(
                                f.imdb_rating::DOUBLE PRECISION
                                / 10.0,
                                0.0
                            )
                        )
                    ) AS rating_score

                FROM films f

                CROSS JOIN query_parameters
                CROSS JOIN user_profile

                LEFT JOIN user_films own_archive
                    ON own_archive.film_id = f.id
                   AND own_archive.user_id = :userId

                WHERE f.embedding IS NOT NULL
                  AND own_archive.id IS NULL
                  AND (
                      1.0 - (
                          f.embedding
                          <=> query_parameters.query_embedding
                      )
                  ) >= :minSimilarity
            """);

        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue(
                            "queryVector",
                            vectorFormatter.toPgVector(
                                queryEmbedding
                            )
                        )
                        .addValue(
                            "minSimilarity",
                            minSimilarity
                        );

        if (intent.yearFrom() > 0) {
            sql.append(
                " AND f.release_year >= :yearFrom\n"
            );
            parameters.addValue(
                "yearFrom",
                intent.yearFrom()
            );
        }

        if (intent.yearTo() > 0) {
            sql.append(
                " AND f.release_year <= :yearTo\n"
            );
            parameters.addValue(
                "yearTo",
                intent.yearTo()
            );
        }

        if (intent.maxRuntimeMinutes() > 0) {
            sql.append("""
                 AND f.runtime_minutes IS NOT NULL
                 AND f.runtime_minutes <= :maxRuntime
                """);

            parameters.addValue(
                "maxRuntime",
                intent.maxRuntimeMinutes()
            );
        }

        if (!"ANY".equals(intent.type())) {
            sql.append(
                " AND UPPER(f.type) = :type\n"
            );
            parameters.addValue(
                "type",
                intent.type()
            );
        }

        if (!intent.includeGenres().isEmpty()) {
            sql.append("""
                 AND EXISTS (
                     SELECT 1

                     FROM UNNEST(
                         STRING_TO_ARRAY(
                             COALESCE(f.genres_text, ''),
                             ','
                         )
                     ) AS genre(value)

                     WHERE LOWER(BTRIM(genre.value))
                           IN (:includeGenres)
                 )
                """);

            parameters.addValue(
                "includeGenres",
                intent.includeGenres()
            );
        }

        if (!intent.excludeGenres().isEmpty()) {
            sql.append("""
                 AND NOT EXISTS (
                     SELECT 1

                     FROM UNNEST(
                         STRING_TO_ARRAY(
                             COALESCE(f.genres_text, ''),
                             ','
                         )
                     ) AS genre(value)

                     WHERE LOWER(BTRIM(genre.value))
                           IN (:excludeGenres)
                 )
                """);

            parameters.addValue(
                "excludeGenres",
                intent.excludeGenres()
            );
        }

        sql.append("""
            ),

            ranked AS (
                SELECT
                    scored.*,

                    (
                        request_score * 0.75
                        + taste_score * 0.15
                        + rating_score * 0.10
                    ) AS total_score

                FROM scored
            )

            SELECT
                imdb_id,
                title,
                year_text,
                type,
                genres_text,
                poster_url,
                total_score

            FROM ranked

            ORDER BY
                total_score DESC,
                request_score DESC,
                title ASC

            LIMIT :limit
            """);

        parameters.addValue(
            "limit",
            Math.max(1, Math.min(limit, 100))
        );

        return jdbcTemplate.query(
            sql.toString(),
            parameters,
            (resultSet, rowNumber) ->
                new BuddyFilmCandidate(
                    resultSet.getString("imdb_id"),
                    resultSet.getString("title"),
                    resultSet.getString("year_text"),
                    resultSet.getString("type"),
                    resultSet.getString("genres_text"),
                    resultSet.getString("poster_url"),
                    resultSet.getDouble("total_score")
                )
        );
    }
}

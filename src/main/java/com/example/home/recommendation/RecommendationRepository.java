package com.example.home.recommendation;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {

    /*
    %70 semantic similarity
    %15 genre preference
    %10 IMDb rating
    %5 catalogue popularity
     */

    private static final String RECOMMENDATION_SQL =
            """
            WITH profile_films AS (

                SELECT
                    f.embedding,
                    f.genres_text

                FROM user_films uf

                JOIN films f
                    ON f.id = uf.film_id

                WHERE uf.user_id = ?
                  AND f.embedding IS NOT NULL
            ),

            user_profile AS (

                SELECT
                    AVG(embedding) AS embedding

                FROM profile_films
            ),

            genre_preferences AS (

                SELECT
                    LOWER(BTRIM(user_genre.value))
                        AS genre,

                    COUNT(*)::DOUBLE PRECISION
                        AS preference_weight

                FROM profile_films pf

                CROSS JOIN LATERAL
                    UNNEST(
                        STRING_TO_ARRAY(
                            COALESCE(
                                pf.genres_text,
                                ''
                            ),
                            ','
                        )
                    ) AS user_genre(value)

                WHERE BTRIM(user_genre.value) <> ''

                GROUP BY
                    LOWER(
                        BTRIM(user_genre.value)
                    )
            ),

            genre_total AS (

                SELECT
                    COALESCE(
                        SUM(preference_weight),
                        0.0
                    ) AS total_weight

                FROM genre_preferences
            ),

            candidate_metrics AS (

                SELECT
                    f.id AS film_id,
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
                            <=> user_profile.embedding
                        )
                    ) AS semantic_score,

                    CASE

                        WHEN genre_total.total_weight = 0.0
                            THEN 0.0

                        ELSE LEAST(
                            1.0,

                            COALESCE(
                                (
                                    SELECT
                                        SUM(
                                            gp.preference_weight
                                        )

                                    FROM UNNEST(
                                        STRING_TO_ARRAY(
                                            COALESCE(
                                                f.genres_text,
                                                ''
                                            ),
                                            ','
                                        )
                                    ) AS candidate_genre(value)

                                    JOIN genre_preferences gp
                                        ON gp.genre =
                                           LOWER(
                                               BTRIM(
                                                   candidate_genre.value
                                               )
                                           )
                                ),
                                0.0
                            )
                            / genre_total.total_weight
                        )

                    END AS genre_score,

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
                    ) AS rating_score,

                    (
                        SELECT
                            COUNT(*)::DOUBLE PRECISION

                        FROM user_films popularity_source

                        WHERE popularity_source.film_id =
                              f.id
                    ) AS popularity_count

                FROM films f

                CROSS JOIN user_profile

                CROSS JOIN genre_total

                LEFT JOIN user_films own_archive
                    ON own_archive.film_id = f.id
                   AND own_archive.user_id = ?

                    WHERE user_profile.embedding IS NOT NULL
                    AND f.embedding IS NOT NULL
                    AND own_archive.id IS NULL
                    AND (
                        1.0 - (
                            f.embedding
                            <=> user_profile.embedding
                        )
                    ) >= ?
                    ),

                    normalized_candidates AS (

                SELECT
                    candidate_metrics.*,

                    MAX(
                        popularity_count
                    ) OVER () AS max_popularity

                FROM candidate_metrics
            ),

            scored_candidates AS (

                SELECT
                    normalized_candidates.*,

                    (
                        semantic_score * 0.70
                        + genre_score * 0.15
                        + rating_score * 0.10
                        + (
                            CASE

                                WHEN max_popularity > 0.0
                                    THEN
                                        LN(
                                            1.0
                                            + popularity_count
                                        )
                                        /
                                        LN(
                                            1.0
                                            + max_popularity
                                        )

                                ELSE 0.0

                            END
                        ) * 0.05
                    ) AS recommendation_score

                FROM normalized_candidates
            )

            SELECT
                film_id,
                imdb_id,
                title,
                year_text,
                type,
                genres_text,
                poster_url,
                recommendation_score

            FROM scored_candidates

            ORDER BY
                recommendation_score DESC,
                semantic_score DESC,
                film_id ASC

            LIMIT ?
            """;


    private final JdbcTemplate jdbcTemplate;


    public RecommendationRepository(
            JdbcTemplate jdbcTemplate
    ) {

        this.jdbcTemplate =
                jdbcTemplate;
    }


    public List<RecommendationCandidate> findCandidates(
            Long userId,
            double minSimilarity,
            int limit
    ) {

        int safeLimit =
                Math.max(
                        1,
                        Math.min(limit, 100)
                );

        return jdbcTemplate.query(
                RECOMMENDATION_SQL,

                (resultSet, rowNumber) ->
                        new RecommendationCandidate(
                                resultSet.getLong(
                                        "film_id"
                                ),
                                resultSet.getString(
                                        "imdb_id"
                                ),
                                resultSet.getString(
                                        "title"
                                ),
                                resultSet.getString(
                                        "year_text"
                                ),
                                resultSet.getString(
                                        "type"
                                ),
                                resultSet.getString(
                                        "genres_text"
                                ),
                                resultSet.getString(
                                        "poster_url"
                                ),
                                resultSet.getDouble(
                                        "recommendation_score"
                                )
                        ),

                userId,
                userId,
                minSimilarity,
                safeLimit
        );
    }
}
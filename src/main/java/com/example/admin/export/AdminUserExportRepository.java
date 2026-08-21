package com.example.admin.export;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.user.UserRole;

@Repository
public class AdminUserExportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminUserExportRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminExportSummary loadSummary(
            String generatedBy,
            OffsetDateTime generatedAt
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS total_users,
                    COUNT(*) FILTER (
                        WHERE enabled = TRUE
                    ) AS enabled_users,
                    COUNT(*) FILTER (
                        WHERE enabled = FALSE
                    ) AS disabled_users,
                    (
                        SELECT COUNT(*)
                        FROM user_films
                    ) AS total_archive_relations,
                    (
                        SELECT COUNT(DISTINCT film_id)
                        FROM user_films
                    ) AS distinct_archived_films
                FROM users
                """,
                Map.of(),
                (resultSet, rowNumber) ->
                        new AdminExportSummary(
                                generatedAt,
                                generatedBy,
                                resultSet.getLong(
                                        "total_users"
                                ),
                                resultSet.getLong(
                                        "enabled_users"
                                ),
                                resultSet.getLong(
                                        "disabled_users"
                                ),
                                resultSet.getLong(
                                        "total_archive_relations"
                                ),
                                resultSet.getLong(
                                        "distinct_archived_films"
                                )
                        )
        );
    }

    public List<UserExportRow> findUsersAfter(
            long lastSeenUserId,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    u.id,
                    u.display_name,
                    u.email,
                    u.role,
                    u.enabled,
                    u.created_at,
                    u.updated_at,
                    COUNT(uf.id) AS archive_film_count,
                    MAX(uf.added_at) AS last_archive_addition
                FROM users u
                LEFT JOIN user_films uf
                    ON uf.user_id = u.id
                WHERE u.id > :lastSeenUserId
                GROUP BY
                    u.id,
                    u.display_name,
                    u.email,
                    u.role,
                    u.enabled,
                    u.created_at,
                    u.updated_at
                ORDER BY u.id ASC
                LIMIT :limit
                """,
                Map.of(
                        "lastSeenUserId",
                        lastSeenUserId,
                        "limit",
                        limit
                ),
                (resultSet, rowNumber) ->
                        mapUser(resultSet)
        );
    }

    public List<UserFilmExportRow> findUserFilmsAfter(
            long lastSeenUserFilmId,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    uf.id AS user_film_id,
                    uf.user_id,
                    u.display_name,
                    u.email,
                    uf.added_at,
                    f.id AS film_id,
                    f.imdb_id,
                    f.title,
                    f.year_text,
                    f.type,
                    f.genres_text,
                    f.imdb_rating
                FROM user_films uf
                INNER JOIN users u
                    ON u.id = uf.user_id
                INNER JOIN films f
                    ON f.id = uf.film_id
                WHERE uf.id > :lastSeenUserFilmId
                ORDER BY uf.id ASC
                LIMIT :limit
                """,
                Map.of(
                        "lastSeenUserFilmId",
                        lastSeenUserFilmId,
                        "limit",
                        limit
                ),
                (resultSet, rowNumber) ->
                        mapUserFilm(resultSet)
        );
    }

    private UserExportRow mapUser(
            ResultSet resultSet
    ) throws SQLException {
        return new UserExportRow(
                resultSet.getLong("id"),
                resultSet.getString("display_name"),
                resultSet.getString("email"),
                UserRole.valueOf(
                        resultSet.getString("role")
                ),
                resultSet.getBoolean("enabled"),
                getOffsetDateTime(
                        resultSet,
                        "created_at"
                ),
                getOffsetDateTime(
                        resultSet,
                        "updated_at"
                ),
                resultSet.getLong(
                        "archive_film_count"
                ),
                getOffsetDateTime(
                        resultSet,
                        "last_archive_addition"
                )
        );
    }

    private UserFilmExportRow mapUserFilm(
            ResultSet resultSet
    ) throws SQLException {
        return new UserFilmExportRow(
                resultSet.getLong(
                        "user_film_id"
                ),
                resultSet.getLong(
                        "user_id"
                ),
                resultSet.getString(
                        "display_name"
                ),
                resultSet.getString(
                        "email"
                ),
                getOffsetDateTime(
                        resultSet,
                        "added_at"
                ),
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
                resultSet.getBigDecimal(
                        "imdb_rating"
                )
        );
    }

    private OffsetDateTime getOffsetDateTime(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        return resultSet.getObject(
                column,
                OffsetDateTime.class
        );
    }
}
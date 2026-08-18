package com.example.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.archive.ArchiveSearchRequest;
import com.example.archive.option.ArchiveSortOption;

@Repository
public class ArchiveSemanticSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    private final VectorFormatter vectorFormatter;

    private final double minSimilarity;


    public ArchiveSemanticSearchRepository(
            JdbcTemplate jdbcTemplate,
            VectorFormatter vectorFormatter,
            @Value("${archive.semantic.min-similarity:0.35}")
            double minSimilarity
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorFormatter = vectorFormatter;
        this.minSimilarity = minSimilarity;
    }


    public Page<Long> search(
            Long userId,
            float[] queryEmbedding,
            ArchiveSearchRequest request,
            Pageable pageable,
            ArchiveSortOption sortOption
    ) {

        String vector =
                vectorFormatter.toPgVector(
                        queryEmbedding
                );


        QueryParts baseQuery =
                createBaseQuery(
                        userId,
                        vector,
                        request
                );


        long total =
                countResults(baseQuery);


        if (total == 0) {

            return new PageImpl<>(
                    List.of(),
                    pageable,
                    0
            );
        }


        StringBuilder sql =
                new StringBuilder(
                        "SELECT uf.id "
                        + baseQuery.sql()
                );


        List<Object> parameters =
                new ArrayList<>(
                        baseQuery.parameters()
                );


        appendSafeOrderBy(
                sql,
                parameters,
                sortOption,
                vector
        );


        sql.append(
                """
                 LIMIT ?
                 OFFSET ?
                """
        );


        parameters.add(
                pageable.getPageSize()
        );

        parameters.add(
                pageable.getOffset()
        );


        List<Long> ids =
                jdbcTemplate.query(
                        sql.toString(),
                        (resultSet, rowNumber) ->
                                resultSet.getLong("id"),
                        parameters.toArray()
                );


        return new PageImpl<>(
                ids,
                pageable,
                total
        );
    }


    private QueryParts createBaseQuery(
            Long userId,
            String vector,
            ArchiveSearchRequest request
    ) {

        StringBuilder sql =
                new StringBuilder(
                        """
                        FROM user_films uf

                        JOIN films f
                            ON f.id = uf.film_id

                        WHERE uf.user_id = ?
                          AND f.embedding IS NOT NULL
                        """
                );


        List<Object> parameters =
                new ArrayList<>();


        /*
         * Ownership her şeyden önce uygulanıyor.
         */
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


        /*
         * <=> cosine distance verir.
         *
         * cosine similarity:
         *
         * 1 - cosineDistance
         */
        sql.append(
                """
                 AND (
                     1 - (
                         f.embedding
                         <=> CAST(? AS vector)
                     )
                 ) >= ?
                """
        );


        parameters.add(vector);

        parameters.add(minSimilarity);


        return new QueryParts(
                sql.toString(),
                parameters
        );
    }


    private long countResults(
            QueryParts baseQuery
    ) {

        String sql =
                "SELECT COUNT(*) "
                + baseQuery.sql();


        Long count =
                jdbcTemplate.queryForObject(
                        sql,
                        Long.class,
                        baseQuery.parameters()
                                .toArray()
                );


        return count != null
                ? count
                : 0L;
    }


    private void appendSafeOrderBy(
            StringBuilder sql,
            List<Object> parameters,
            ArchiveSortOption sortOption,
            String vector
    ) {

        switch (sortOption) {

            case RELEVANCE_DESC -> {

                sql.append(
                        """
                         ORDER BY
                            f.embedding
                                <=> CAST(? AS vector) ASC,
                            uf.added_at DESC,
                            uf.id DESC
                        """
                );

                parameters.add(vector);
            }


            case ADDED_AT_DESC ->
                    sql.append(
                            """
                             ORDER BY
                                uf.added_at DESC,
                                uf.id DESC
                            """
                    );


            case ADDED_AT_ASC ->
                    sql.append(
                            """
                             ORDER BY
                                uf.added_at ASC,
                                uf.id ASC
                            """
                    );


            case TITLE_ASC ->
                    sql.append(
                            """
                             ORDER BY
                                LOWER(f.title) ASC,
                                uf.id ASC
                            """
                    );


            case TITLE_DESC ->
                    sql.append(
                            """
                             ORDER BY
                                LOWER(f.title) DESC,
                                uf.id DESC
                            """
                    );


            case YEAR_DESC ->
                    sql.append(
                            """
                             ORDER BY
                                f.release_year DESC NULLS LAST,
                                uf.id DESC
                            """
                    );


            case YEAR_ASC ->
                    sql.append(
                            """
                             ORDER BY
                                f.release_year ASC NULLS LAST,
                                uf.id ASC
                            """
                    );
        }
    }


    private record QueryParts(
            String sql,
            List<Object> parameters
    ) {
    }
}

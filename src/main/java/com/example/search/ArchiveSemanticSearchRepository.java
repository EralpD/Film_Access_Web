package com.example.search;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.example.archive.ArchiveSearchRequest;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.archive.response.ArchiveSearchResult;

@Repository
public class ArchiveSemanticSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final VectorFormatter vectors;
    private final String model;
    private final double semanticThreshold, fuzzyThreshold;
    private final int candidateLimit;

    @Autowired
    public ArchiveSemanticSearchRepository(NamedParameterJdbcTemplate jdbc, VectorFormatter vectors,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model,
            @Value("${archive.semantic.min-similarity:0.35}") double semanticThreshold,
            @Value("${archive.search.fuzzy-threshold:0.35}") double fuzzyThreshold,
            @Value("${archive.search.spelling-candidates:200}") int candidateLimit) {
        if (!Double.isFinite(semanticThreshold) || semanticThreshold < 0 || semanticThreshold > 1
                || !Double.isFinite(fuzzyThreshold) || fuzzyThreshold < 0 || fuzzyThreshold > 1
                || candidateLimit < 1 || candidateLimit > 2000)
            throw new IllegalArgumentException("Invalid search thresholds or candidate limit.");
        this.jdbc = jdbc; this.vectors = vectors; this.model = model;
        this.semanticThreshold = semanticThreshold; this.fuzzyThreshold = fuzzyThreshold;
        this.candidateLimit = candidateLimit;
    }

    public ArchiveSemanticSearchRepository(NamedParameterJdbcTemplate jdbc, VectorFormatter vectors,
            String model, double semanticThreshold, double fuzzyThreshold) {
        this(jdbc, vectors, model, semanticThreshold, fuzzyThreshold, 200);
    }

    private static final String CURRENT = """
        coalesce(f.embedding IS NOT NULL AND f.embedding_model = :model
            AND f.embedding_content_hash = f.search_content_hash, false)
        """;

    // Null scope is only used by the explicit shared-catalog entry points. Archive methods require a user ID.
    private String filters(Long userId, ArchiveSearchRequest r) {
        String sql = " FROM films f ";
        if (userId != null) sql += " JOIN user_films uf ON uf.film_id = f.id AND uf.user_id = :userId ";
        sql += " WHERE true ";
        if (r.getYearFrom() != null) sql += " AND f.release_year >= :yearFrom";
        if (r.getYearTo() != null) sql += " AND f.release_year <= :yearTo";
        if (r.getType() != null) sql += " AND upper(f.type) = :type";
        if (r.hasGenre()) sql += " AND lower(f.genres_text) LIKE lower(:genre) ESCAPE '\\'";
        if (r.getActor() != null && !r.getActor().isBlank()) sql += personFilter("actor");
        if (r.getDirector() != null && !r.getDirector().isBlank()) sql += personFilter("director");
        return sql;
    }
    private String personFilter(String role) {
        return " AND EXISTS (SELECT 1 FROM film_search_people pf WHERE pf.film_id = f.id"
                + " AND pf.role = '" + role + "' AND pf.name = :" + role + ")";
    }
    private Map<String, Object> parameters(Long userId, ArchiveSearchRequest r, Pageable page) {
        Map<String, Object> p = new HashMap<>();
        String q = SearchText.normalize(r.getQuery());
        p.put("userId", userId); p.put("model", model); p.put("q", q);
        p.put("queryGenre", SearchText.genre(r.getQuery()));
        p.put("words", q); // string_to_array is applied to the input once, never to each stored name.
        p.put("yearFrom", r.getYearFrom()); p.put("yearTo", r.getYearTo());
        p.put("type", r.getType() == null ? null : r.getType().name());
        String genre = r.getGenre() == null ? "" : SearchText.genreFilter(r.getGenre().trim());
        p.put("genre", "%" + genre.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        p.put("actor", SearchText.normalize(r.getActor())); p.put("director", SearchText.normalize(r.getDirector()));
        p.put("fuzzyThreshold", fuzzyThreshold); p.put("semanticThreshold", semanticThreshold);
        p.put("candidateLimit", candidateLimit); p.put("candidateFetch", candidateLimit + 1);
        if (page != null) { p.put("limit", page.getPageSize()); p.put("offset", page.getOffset()); }
        return p;
    }

    public Coverage coverage(long userId, ArchiveSearchRequest r) { return coverageFor(userId, r); }
    public Coverage catalogCoverage(ArchiveSearchRequest r) { return coverageFor(null, r); }
    private Coverage coverageFor(Long userId, ArchiveSearchRequest r) {
        return jdbc.queryForObject("SELECT count(*) AS total, count(*) FILTER (WHERE " + CURRENT
                + ") AS indexed " + filters(userId, r), parameters(userId, r, null),
                (rs, row) -> new Coverage(rs.getLong("total"), rs.getLong("indexed")));
    }

    public Optional<ArchiveSearchResult> directSearch(long userId, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        return directSearchFor(userId, r, page, sort);
    }
    public Optional<ArchiveSearchResult> directCatalogSearch(ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        return directSearchFor(null, r, page, sort);
    }

    /**
     * Builds the default catalog page from the authenticated user's archive.
     * A profile needs several current embeddings before it is meaningful; until
     * then a daily, deterministic shuffle keeps pagination stable.
     */
    public ArchiveSearchResult browseCatalog(long userId, Pageable page, int minProfileFilms) {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", userId);
        p.put("model", model);
        p.put("minProfileFilms", Math.max(1, minProfileFilms));
        p.put("limit", page.getPageSize());
        p.put("offset", page.getOffset());

        String sql = """
            WITH profile_films AS MATERIALIZED (
                SELECT f.embedding, f.genres_text
                FROM user_films uf
                JOIN films f ON f.id = uf.film_id
                WHERE uf.user_id = :userId
                  AND coalesce(f.embedding IS NOT NULL AND f.embedding_model = :model
                      AND f.embedding_content_hash = f.search_content_hash, false)
            ), user_profile AS (
                SELECT count(*) AS film_count, avg(embedding) AS embedding
                FROM profile_films
            ), genre_preferences AS (
                SELECT lower(btrim(g.value)) AS genre, count(*)::double precision AS preference_weight
                FROM profile_films pf
                CROSS JOIN LATERAL unnest(string_to_array(coalesce(pf.genres_text, ''), ',')) AS g(value)
                WHERE btrim(g.value) <> ''
                GROUP BY lower(btrim(g.value))
            ), genre_total AS (
                SELECT coalesce(sum(preference_weight), 0.0) AS total_weight
                FROM genre_preferences
            ), candidate_metrics AS MATERIALIZED (
                SELECT f.id AS user_film_id, f.created_at AS added_at,
                    f.imdb_id, f.title, f.year_text, f.release_year, f.type,
                    f.genres_text, f.poster_url,
                    user_profile.film_count >= :minProfileFilms AS personalized,
                    coalesce(f.embedding IS NOT NULL AND f.embedding_model = :model
                        AND f.embedding_content_hash = f.search_content_hash, false) AS indexed,
                    CASE WHEN f.embedding IS NOT NULL AND user_profile.embedding IS NOT NULL
                        THEN greatest(0.0, 1.0 - (f.embedding <=> user_profile.embedding))
                        ELSE 0.0 END AS semantic_score,
                    CASE WHEN genre_total.total_weight = 0.0 THEN 0.0 ELSE least(1.0,
                        coalesce((SELECT sum(gp.preference_weight)
                            FROM unnest(string_to_array(coalesce(f.genres_text, ''), ',')) AS candidate_genre(value)
                            JOIN genre_preferences gp ON gp.genre = lower(btrim(candidate_genre.value))), 0.0)
                            / genre_total.total_weight) END AS genre_score,
                    least(1.0, greatest(0.0, coalesce(f.imdb_rating::double precision / 10.0, 0.0)))
                        AS rating_score,
                    (SELECT count(*)::double precision FROM user_films popularity
                        WHERE popularity.film_id = f.id) AS popularity_count
                FROM films f
                CROSS JOIN user_profile
                CROSS JOIN genre_total
                LEFT JOIN user_films own_archive
                    ON own_archive.film_id = f.id AND own_archive.user_id = :userId
                WHERE own_archive.id IS NULL
            ), normalized_candidates AS (
                SELECT candidate_metrics.*, max(popularity_count) OVER () AS max_popularity
                FROM candidate_metrics
            ), scored_candidates AS MATERIALIZED (
                SELECT normalized_candidates.*,
                    semantic_score * 0.70
                    + genre_score * 0.15
                    + rating_score * 0.10
                    + CASE WHEN max_popularity > 0.0
                        THEN ln(1.0 + popularity_count) / ln(1.0 + max_popularity)
                        ELSE 0.0 END * 0.05 AS recommendation_score
                FROM normalized_candidates
            ), ranked_candidates AS MATERIALIZED (
                SELECT scored_candidates.*,
                    row_number() OVER (ORDER BY
                        CASE WHEN personalized THEN recommendation_score END DESC NULLS LAST,
                        md5(CAST(user_film_id AS text) || ':' || CAST(:userId AS text)
                            || ':' || CURRENT_DATE::text),
                        user_film_id) AS browse_rank
                FROM scored_candidates
            ), totals AS (
                SELECT count(*) AS match_count, count(*) AS eligible_count,
                    count(*) FILTER (WHERE NOT indexed) AS unindexed_count,
                    false AS limited
                FROM ranked_candidates
            ), page AS (
                SELECT user_film_id, added_at, imdb_id, title, year_text, release_year,
                    type, genres_text, poster_url,
                    CASE WHEN personalized THEN 'Recommended for you' ELSE NULL END AS reason,
                    browse_rank
                FROM ranked_candidates
                ORDER BY browse_rank
                LIMIT :limit OFFSET :offset
            )
            SELECT totals.*, page.*
            FROM totals LEFT JOIN page ON true
            ORDER BY page.browse_rank
            """;

        return read(sql, p, page, "catalog-browse");
    }

    private String strongCandidates(Long userId, ArchiveSearchRequest r) {
        String filter = filters(userId, r);
        if (!r.hasSemanticQuery()) return "SELECT f.id, 0 AS priority, 'Filtered result' AS reason " + filter;
        String people = " SELECT p.film_id AS id, CASE WHEN p.role = 'director' THEN 1 ELSE 2 END AS priority,"
                + " CASE WHEN p.role = 'director' THEN 'Director match' ELSE 'Actor match' END AS reason"
                + " FROM film_search_people p WHERE :q <> '' AND (length(:q) >= 3 OR p.name = :q)"
                + " AND p.words @> string_to_array(:words, ' ') AND p.film_id IN (SELECT f.id " + filter + ")";
        return "SELECT f.id, 0 AS priority, 'Title match' AS reason " + filter + " AND :q <> '' AND f.search_title = :q"
                + " UNION ALL " + people
                + " UNION ALL SELECT f.id, 3, 'Genre match' " + filter
                + " AND f.search_genres @> ARRAY[lower(CAST(:queryGenre AS text))]";
    }

    private Optional<ArchiveSearchResult> directSearchFor(Long userId, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        var p = parameters(userId, r, page);
        ArchiveSearchResult result = directPage(userId, r, page, sort, p, strongCandidates(userId, r), "direct");
        if (!r.hasSemanticQuery() || result.page().getTotalElements() > 0) return Optional.of(result);
        // Only a unique, one-edit title correction can bypass meaning search. Ambiguous corrections cannot.
        if (SearchText.normalize(r.getQuery()).length() < 4 || SearchText.normalize(r.getQuery()).length() > 80
                || SearchText.normalize(r.getQuery()).split(" ").length > 3) return Optional.empty();
        String correction = """
            WITH nearest AS MATERIALIZED (
                SELECT f.search_title %s ORDER BY f.search_title <-> :q LIMIT 8
            ), corrections AS (
                SELECT DISTINCT search_title FROM nearest
                WHERE similarity(search_title, :q) >= 0.15 AND length(search_title) <= 255
                  AND abs(length(search_title) - length(:q)) <= 1
                  AND levenshtein_less_equal(left(search_title, 255), :q, 1) <= 1
            ) SELECT min(search_title) FROM corrections HAVING count(*) = 1
                AND max(similarity(search_title, :q)) >= 0.45
                AND ((SELECT count(*) FROM nearest) < 8
                    OR (SELECT min(similarity(search_title, :q)) FROM nearest) < 0.15)
            """.formatted(filters(userId, r));
        List<String> corrections = jdbc.queryForList(correction, p, String.class);
        if (corrections.isEmpty()) return Optional.empty();
        p.put("correction", corrections.getFirst());
        return Optional.of(directPage(userId, r, page, sort, p,
                "SELECT f.id, 4 AS priority, 'Spelling match' AS reason " + filters(userId, r)
                        + " AND f.search_title = :correction", "spelling"));
    }

    private ArchiveSearchResult directPage(Long userId, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort,
            Map<String, Object> p, String candidates, String mode) {
        String sql = """
            WITH candidates AS (%s), matches AS MATERIALIZED (
                SELECT id, min(priority) AS priority, (array_agg(reason ORDER BY priority))[1] AS reason
                FROM candidates GROUP BY id
            ), combined AS (
                SELECT %s, m.priority, m.reason, 0 AS relevance %s JOIN matches m ON m.id = f.id
            ), totals AS (SELECT count(*) AS match_count, 0 AS eligible_count, 0 AS unindexed_count,
                false AS limited FROM matches), page AS (
                SELECT * FROM combined ORDER BY %s LIMIT :limit OFFSET :offset
            ) SELECT totals.*, page.* FROM totals LEFT JOIN page ON true ORDER BY %s
            """.formatted(candidates, projection(userId), projectionFrom(userId), orderBy(sort), orderBy(sort));
        return read(sql, p, page, mode);
    }

    public ArchiveSearchResult search(long userId, float[] vector, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        return searchFor(userId, vector, r, page, sort);
    }
    public ArchiveSearchResult searchCatalog(float[] vector, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        return searchFor(null, vector, r, page, sort);
    }

    private ArchiveSearchResult searchFor(Long userId, float[] vector, ArchiveSearchRequest r, Pageable page, ArchiveSortOption sort) {
        var p = parameters(userId, r, page);
        p.put("vector", vector == null ? null : vectors.toPgVector(vector));
        String sql = """
            WITH eligible AS MATERIALIZED (SELECT f.id %s),
            near_titles AS MATERIALIZED (
                SELECT f.id, f.search_title, f.search_title <-> :q AS distance FROM films f
                WHERE f.id IN (SELECT id FROM eligible) AND length(:q) BETWEEN 3 AND 80
                    AND f.search_title <-> :q <= 1 - least(:fuzzyThreshold, 0.15)
                ORDER BY f.search_title <-> :q, f.id LIMIT :candidateFetch
            ), near_words AS MATERIALIZED (
                SELECT f.id, f.search_title, f.search_title <->>> :q AS distance FROM films f
                WHERE f.id IN (SELECT id FROM eligible) AND length(:q) BETWEEN 3 AND 80
                    AND f.search_title <->>> :q <= 1 - :fuzzyThreshold
                ORDER BY f.search_title <->>> :q, f.id LIMIT :candidateFetch
            ), title_pool AS (
                (SELECT id, search_title FROM near_titles ORDER BY distance, id LIMIT :candidateLimit)
                UNION (SELECT id, search_title FROM near_words ORDER BY distance, id LIMIT :candidateLimit)
            ), title_scores AS MATERIALIZED (
                SELECT id, search_title,
                    greatest(similarity(search_title, :q), strict_word_similarity(:q, search_title)) AS score
                FROM title_pool
            ), near_people AS MATERIALIZED (
                SELECT p.*, p.name <->>> :q AS distance FROM film_search_people p
                WHERE p.film_id IN (SELECT id FROM eligible) AND length(:q) BETWEEN 4 AND 80
                    AND p.name <->>> :q <= 1 - greatest(:fuzzyThreshold, 0.55)
                ORDER BY p.name <->>> :q, p.film_id, p.role, p.name LIMIT :candidateFetch
            ), people_pool AS (
                SELECT * FROM near_people ORDER BY distance, film_id, role, name LIMIT :candidateLimit
            ), lexical_raw AS (
                SELECT id, priority, reason, 1.0 AS score FROM (%s) strong
                UNION ALL SELECT f.id, 3, 'Title contains search text', 0.9 FROM films f
                    WHERE f.id IN (SELECT id FROM eligible) AND length(:q) >= 3
                      AND f.search_title LIKE '%%' || :q || '%%'
                UNION ALL SELECT id, 4, 'Spelling match', score FROM title_scores
                    WHERE score >= :fuzzyThreshold OR (score >= 0.15 AND length(search_title) <= 255
                        AND abs(length(search_title) - length(:q)) <= CASE WHEN length(:q) <= 5 THEN 1 ELSE 2 END
                        AND levenshtein_less_equal(left(search_title, 255), left(:q, 255),
                            CASE WHEN length(:q) <= 5 THEN 1 ELSE 2 END) <= CASE WHEN length(:q) <= 5 THEN 1 ELSE 2 END)
                UNION ALL SELECT film_id, 4,
                    CASE WHEN role = 'director' THEN 'Similar director name' ELSE 'Similar actor name' END, 1 - distance
                    FROM people_pool WHERE 1 - distance >= greatest(:fuzzyThreshold, 0.55)
                UNION ALL SELECT p.film_id, 3,
                    CASE WHEN p.role = 'director' THEN 'Director in search text' ELSE 'Actor in search text' END, 0.8
                    FROM film_search_people p WHERE p.film_id IN (SELECT id FROM eligible)
                      AND cardinality(p.words) >= 2 AND p.words <@ string_to_array(:words, ' ')
            ), lexical_matches AS (
                SELECT id, min(priority) AS priority, max(score) AS score,
                    (array_agg(reason ORDER BY priority, reason))[1] AS reason
                FROM lexical_raw GROUP BY id
            ), lexical AS (
                SELECT *, row_number() OVER (ORDER BY priority, score DESC, id) AS rank FROM lexical_matches
            ), semantic_scores AS MATERIALIZED (
                SELECT f.id, 1 - (f.embedding <=> CAST(:vector AS vector)) AS score
                FROM films f WHERE CAST(:vector AS vector) IS NOT NULL
                    AND f.id IN (SELECT id FROM eligible) AND %s
            ), semantic AS (
                SELECT id, row_number() OVER (ORDER BY score DESC, id) AS rank
                FROM semantic_scores WHERE score >= :semanticThreshold
            ), matches AS MATERIALIZED (
                SELECT coalesce(l.id, s.id) AS id, coalesce(l.priority, 5) AS priority,
                    coalesce(l.reason, 'Meaning match') AS reason,
                    coalesce(1.0 / (60 + l.rank), 0) + coalesce(1.0 / (60 + s.rank), 0) AS relevance
                FROM lexical l FULL JOIN semantic s ON l.id = s.id
            ), combined AS (
                SELECT %s, m.priority, m.reason, m.relevance %s JOIN matches m ON m.id = f.id
            ), totals AS (
                SELECT (SELECT count(*) FROM eligible) AS eligible_count,
                    (SELECT count(*) FROM films f WHERE f.id IN (SELECT id FROM eligible) AND NOT %s) AS unindexed_count,
                    (SELECT count(*) FROM matches) AS match_count,
                    ((SELECT count(*) FROM near_titles) > :candidateLimit
                     OR (SELECT count(*) FROM near_words) > :candidateLimit
                     OR (SELECT count(*) FROM near_people) > :candidateLimit) AS limited
            ), page AS (SELECT * FROM combined ORDER BY %s LIMIT :limit OFFSET :offset)
            SELECT totals.*, page.* FROM totals LEFT JOIN page ON true ORDER BY %s
            """.formatted(filters(userId, r), strongCandidates(userId, r), CURRENT,
                    projection(userId), projectionFrom(userId), CURRENT, orderBy(sort), orderBy(sort));
        return read(sql, p, page, "hybrid");
    }

    private String projection(Long userId) {
        return (userId == null ? "f.id AS user_film_id, f.created_at AS added_at," : "uf.id AS user_film_id, uf.added_at,")
                + " f.imdb_id, f.title, f.year_text, f.release_year, f.type, f.genres_text, f.poster_url";
    }
    private String projectionFrom(Long userId) {
        return userId == null ? " FROM films f "
                : " FROM films f JOIN user_films uf ON uf.film_id = f.id AND uf.user_id = :userId ";
    }
    private ArchiveSearchResult read(String sql, Map<String, Object> p, Pageable page, String mode) {
        return jdbc.query(sql, p, rs -> {
            List<ArchiveFilmResponse> films = new ArrayList<>();
            long total = 0, eligible = 0, missing = 0; boolean limited = false;
            while (rs.next()) {
                total = rs.getLong("match_count"); eligible = rs.getLong("eligible_count");
                missing = rs.getLong("unindexed_count"); limited = rs.getBoolean("limited");
                Long id = rs.getObject("user_film_id", Long.class);
                if (id != null) films.add(new ArchiveFilmResponse(id, rs.getString("imdb_id"), rs.getString("title"),
                        rs.getString("year_text"), rs.getString("type"), rs.getString("genres_text"),
                        rs.getString("poster_url"), rs.getObject("added_at", OffsetDateTime.class), rs.getString("reason")));
            }
            return new ArchiveSearchResult(new PageImpl<>(films, page, total), eligible, missing, false, limited, mode);
        });
    }
    private String orderBy(ArchiveSortOption sort) {
        return switch (sort) {
            case RELEVANCE_DESC -> "CASE WHEN priority <= 2 THEN priority ELSE 3 END, relevance DESC, priority, added_at DESC, user_film_id DESC";
            case ADDED_AT_DESC -> "added_at DESC, user_film_id DESC";
            case ADDED_AT_ASC -> "added_at ASC, user_film_id ASC";
            case TITLE_ASC -> "lower(title), user_film_id";
            case TITLE_DESC -> "lower(title) DESC, user_film_id DESC";
            case YEAR_DESC -> "release_year DESC NULLS LAST, user_film_id DESC";
            case YEAR_ASC -> "release_year ASC NULLS LAST, user_film_id ASC";
        };
    }
    public record Coverage(long total, long indexed) {}
}

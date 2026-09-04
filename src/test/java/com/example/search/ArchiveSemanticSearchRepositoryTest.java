package com.example.search;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.example.archive.ArchiveSearchRequest;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveSearchResult;
import com.example.film.Film;
import com.example.search.service.FilmEmbeddingService;

@Testcontainers
class ArchiveSemanticSearchRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17").asCompatibleSubstituteFor("postgres"));
    static JdbcTemplate jdbc;
    ArchiveSemanticSearchRepository repository;
    static final String MODEL = "text-embedding-3-small";
    static final VectorFormatter VECTORS = new VectorFormatter();

    @BeforeAll
    static void migrate() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).target("9").load().migrate();
        jdbc = new JdbcTemplate(ds);
        jdbc.update("""
            INSERT INTO films(imdb_id,title,year_text,type,embedding,embedding_model,embedding_content_hash)
            VALUES ('tt0000001','Legacy','2000','movie',CAST(? AS vector),?,
                encode(sha256(convert_to(E'Title: Legacy\\r\\nYear: 2000\\r\\nType: movie\\r\\n','UTF8')),'hex'))
            """, VECTORS.toPgVector(vector(1)), MODEL);
        Flyway.configure().dataSource(ds).target("10").load().migrate();
        assertThat(jdbc.queryForObject("SELECT embedding_content_hash = search_content_hash FROM films", Boolean.class)).isTrue();
        jdbc.update("UPDATE films SET director = 'Christopher Nolan', actors = 'Tom Hardy, N/A, Tom Hardy'");
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film_search_people", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            SELECT embedding IS NOT NULL
            FROM films WHERE imdb_id = 'tt0000001'
            """, Boolean.class)).isTrue();
    }

    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE user_films, films, users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(email,display_name,password_hash) VALUES ('one@test','One','x'),('two@test','Two','x')");
        repository = new ArchiveSemanticSearchRepository(new NamedParameterJdbcTemplate(jdbc), VECTORS, MODEL, .35, .35);
    }

    @Test
    void exactAndMisspelledTitlesWorkWithoutEmbeddingsAndNeverLeakOtherArchives() {
        add("Interstellar", 2014, "Adventure", 1, null);
        add("Interstellar", 2014, "Adventure", 2, 1.0);
        assertThat(titles(search("Interstelar"))).containsExactly("Interstellar");
        assertThat(search("Interstellar").unindexedFilms()).isEqualTo(1);
        assertThat(search("Interstellar").page().getTotalElements()).isEqualTo(1);
    }

    @Test
    void semanticRanksByCosineAndExactTitleWinsOverSemanticScore() {
        add("Interstellar", 2014, "Adventure", 1, .4);
        add("Apollo", 1995, "Drama", 1, .99);
        add("Unrelated", 2000, "Comedy", 1, .1);
        assertThat(titles(search("Interstellar"))).containsExactly("Interstellar", "Apollo");
        assertThat(titles(search("journey through the cosmos"))).containsExactly("Apollo", "Interstellar");
    }

    @Test
    void oldModelAndChangedContentVectorsAreExcludedButTheirTitlesRemainSearchable() {
        long changed = add("Changed", 2001, "Drama", 1, 1.0);
        long oldModel = add("Old model", 2002, "Drama", 1, 1.0);
        jdbc.update("UPDATE films SET plot = 'Updated plot' WHERE id = ?", changed);
        jdbc.update("UPDATE films SET embedding_model = 'other-model' WHERE id = ?", oldModel);
        assertThat(search("journey through the cosmos").page()).isEmpty();
        assertThat(search("Changed").unindexedFilms()).isEqualTo(2);
        assertThat(titles(search("Changed"))).containsExactly("Changed");
    }

    @Test
    void allBranchesRespectYearsTypeAndLiteralGenreWildcards() {
        add("Space One", 2010, "Drama", 1, 1.0);
        add("Space Two", 2020, "Drama_100%", 1, 1.0);
        var request = request("Space");
        request.setYearFrom(2015);
        request.setGenre("_100%");
        request.setType(com.example.omdb.dto.OmdbType.MOVIE);
        var result = repository.search(1, vector(1), request, PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC);
        assertThat(titles(result)).containsExactly("Space Two");
        request.setGenre("%");
        request.setYearTo(2015);
        assertThat(repository.coverage(1, request).total()).isZero();
    }

    @Test
    void unionDeduplicatesAndPaginationHasStableTotalsIncludingOutOfRangePages() {
        add("Space One", 2010, "Drama", 1, .9);
        add("Space Two", 2011, "Drama", 1, .8);
        var request = request("Space");
        var first = repository.search(1, vector(1), request, PageRequest.of(0, 1), ArchiveSortOption.RELEVANCE_DESC);
        var second = repository.search(1, vector(1), request, PageRequest.of(1, 1), ArchiveSortOption.RELEVANCE_DESC);
        var beyond = repository.search(1, vector(1), request, PageRequest.of(20, 1), ArchiveSortOption.RELEVANCE_DESC);
        assertThat(first.page().getTotalElements()).isEqualTo(2);
        assertThat(second.page().getTotalElements()).isEqualTo(2);
        assertThat(titles(first)).doesNotContainAnyElementsOf(titles(second));
        assertThat(beyond.page()).isEmpty();
        assertThat(beyond.page().getTotalElements()).isEqualTo(2);
    }

    @Test
    void catalogBrowseRanksUnownedFilmsByUserProfileAndPaginates() {
        add("Profile One", 2010, "Drama", 1, 1.0);
        add("Profile Two", 2011, "Drama", 1, 1.0);
        add("Profile Three", 2012, "Drama", 1, 1.0);
        add("Far Candidate", 2020, "Drama", 2, .2);
        add("Near Candidate", 2021, "Drama", 2, .9);

        var first = repository.browseCatalog(1, PageRequest.of(0, 1), 3);
        var second = repository.browseCatalog(1, PageRequest.of(1, 1), 3);

        assertThat(titles(first)).containsExactly("Near Candidate");
        assertThat(titles(second)).containsExactly("Far Candidate");
        assertThat(first.page().getTotalElements()).isEqualTo(2);
        assertThat(first.page().getContent().getFirst().matchReason()).isEqualTo("Recommended for you");
    }

    @Test
    void catalogBrowseUsesStableShuffleWhenProfileIsTooSmall() {
        add("Only Profile Film", 2010, "Drama", 1, 1.0);
        add("Candidate One", 2020, "Comedy", 2, .1);
        add("Candidate Two", 2021, "Action", 2, .9);
        add("Candidate Three", 2022, "Sci-Fi", 2, .5);

        var first = repository.browseCatalog(1, PageRequest.of(0, 2), 3);
        var repeated = repository.browseCatalog(1, PageRequest.of(0, 2), 3);
        var second = repository.browseCatalog(1, PageRequest.of(1, 2), 3);

        assertThat(titles(first)).containsExactlyElementsOf(titles(repeated));
        assertThat(titles(first)).doesNotContainAnyElementsOf(titles(second));
        assertThat(first.page().getTotalElements()).isEqualTo(3);
        assertThat(first.page().getContent()).allMatch(film -> film.matchReason() == null);
    }

    @Test
    void explicitSortAndNullVectorFallbackAreRespected() {
        add("Space Z", 2010, "Drama", 1, 1.0);
        add("Space A", 2020, "Drama", 1, .8);
        var result = repository.search(1, null, request("Space"), PageRequest.of(0, 20), ArchiveSortOption.TITLE_ASC);
        assertThat(titles(result)).containsExactly("Space A", "Space Z");
    }

    @Test
    void shortTitlesDoNotTriggerBroadFuzzyMatchesAndAccentsAreNormalized() {
        add("Up", 2009, "Animation", 1, null);
        add("Super", 2010, "Comedy", 1, null);
        add("Çağrı", 1976, "Drama", 1, null);
        assertThat(titles(search("up"))).containsExactly("Up");
        assertThat(titles(search("cagri"))).containsExactly("Çağrı");
        assertThat(search("zzzzzzzzzzzzzz").page()).isEmpty();
    }

    @Test
    void generatedHashMatchesJavaAndStaleWritesCannotOverwriteChangedContent() {
        long id = add(" Inception ", 2010, "Sci-Fi", 1, null);
        jdbc.update("UPDATE films SET plot = ?, director = ?, actors = ? WHERE id = ?",
                "Dreams\ninside dreams.", "N/A", "  Actor  ", id);
        Film film = Film.createFromOmdb("tt0000001", " Inception ", "2010", (short)2010, "movie");
        film.setGenresText("Sci-Fi");
        film.setPlot("Dreams\ninside dreams.");
        film.setDirector("N/A");
        film.setActors("  Actor  ");
        var service = new FilmEmbeddingService(org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class), MODEL);
        String hash = service.createContentHash(film);
        assertThat(jdbc.queryForObject("SELECT search_content_hash FROM films WHERE id = ?", String.class, id)).isEqualTo(hash);
        jdbc.update("UPDATE films SET plot = 'Different' WHERE id = ?", id);
        new FilmEmbeddingRepository(jdbc, VECTORS).updateEmbedding(id, vector(1), MODEL, hash);
        assertThat(jdbc.queryForObject("SELECT embedding IS NULL FROM films WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void levenshteinCanRecoverACandidateBelowTheTrigramThreshold() {
        add("Inception", 2010, "Drama", 1, null);
        repository = new ArchiveSemanticSearchRepository(new NamedParameterJdbcTemplate(jdbc), VECTORS, MODEL, .35, .99);
        assertThat(titles(search("Incepton"))).containsExactly("Inception");
    }

    @Test
    void semanticThresholdExcludesWeakCandidatesWithoutDroppingExactTitles() {
        add("Below", 2010, "Drama", 1, .34);
        add("Above", 2010, "Drama", 1, .36);
        assertThat(titles(search("journey through the cosmos"))).containsExactly("Above");
        assertThat(titles(search("Below"))).containsExactly("Below", "Above");
    }

    @Test
    void repairRetriesArePersistentBoundedAndRestrictedToArchivedFilms() {
        long id = add("Pending", 2010, "Drama", 1, null);
        jdbc.update("INSERT INTO films(imdb_id,title) VALUES ('tt9999999','Not archived')");
        var films = org.mockito.Mockito.mock(com.example.film.FilmRepository.class);
        var indexer = org.mockito.Mockito.mock(com.example.search.service.FilmSemanticIndexService.class);
        var embeddings = org.mockito.Mockito.mock(FilmEmbeddingService.class);
        org.mockito.Mockito.when(embeddings.getEmbeddingModelName()).thenReturn(MODEL);
        org.mockito.Mockito.when(films.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        org.mockito.Mockito.when(indexer.indexFilms(org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new IllegalStateException("Provider unavailable"));
        var worker = new com.example.search.service.ArchiveEmbeddingRepairWorker(jdbc, films, indexer, embeddings, 16);
        worker.repair();
        worker.repair(); // Retry time has not arrived.
        org.mockito.Mockito.verify(indexer, org.mockito.Mockito.times(1)).indexFilms(org.mockito.ArgumentMatchers.anyList());
        for (int attempt = 0; attempt < 2; attempt++) {
            jdbc.update("UPDATE films SET embedding_retry_after = CURRENT_TIMESTAMP - INTERVAL '1 minute'");
            worker.repair();
        }
        jdbc.update("UPDATE films SET embedding_retry_after = CURRENT_TIMESTAMP - INTERVAL '1 minute'");
        worker.repair(); // Three failures exhaust this model/content revision's budget.
        org.mockito.Mockito.verify(indexer, org.mockito.Mockito.times(3)).indexFilms(org.mockito.ArgumentMatchers.anyList());
        assertThat(jdbc.queryForObject("SELECT embedding_retry_count FROM films WHERE id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT embedding_retry_count FROM films WHERE imdb_id = 'tt9999999'", Integer.class)).isZero();
        jdbc.update("UPDATE films SET plot = 'New content' WHERE id = ?", id);
        worker.repair();
        assertThat(jdbc.queryForObject("SELECT embedding_retry_count FROM films WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    static float[] vector(double similarity) {
        float[] v = new float[1536];
        v[0] = (float) similarity;
        v[1] = (float) Math.sqrt(1 - similarity * similarity);
        return v;
    }

    @Test
    void peopleSearchMatchesIndividualNamesAndKeepsOwnership() {
        long dunkirk = add("Dunkirk", 2017, "War", 1, null);
        long tenet = add("Tenet", 2020, "Sci-Fi", 2, null);
        jdbc.update("UPDATE films SET director = 'Christopher Nolan', actors = 'Tom Hardy, Cillian Murphy' WHERE id = ?", dunkirk);
        jdbc.update("UPDATE films SET director = 'Christopher Nolan' WHERE id = ?", tenet);
        assertThat(titles(direct("Nolan").orElseThrow())).containsExactly("Dunkirk");
        assertThat(titles(direct("Tom Hardy").orElseThrow())).containsExactly("Dunkirk");
        assertThat(direct("Tom Murphy")).isEmpty(); // Cannot combine tokens from different actors.
        assertThat(titles(repository.directCatalogSearch(request("Nolan"), PageRequest.of(0, 20),
                ArchiveSortOption.RELEVANCE_DESC).orElseThrow())).containsExactlyInAnyOrder("Dunkirk", "Tenet");
        assertThat(search("Nolan").page().getContent().getFirst().matchReason()).isEqualTo("Director match");
        assertThat(titles(search("Nolann"))).contains("Dunkirk");
    }

    @Test
    void creditsProjectionUpdatesAtomicallyAndDeletesWithFilm() {
        long id = add("Dunkirk", 2017, "War", 1, null);
        jdbc.update("UPDATE films SET actors = ' Tom Hardy, N/A, Tom Hardy, ' WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film_search_people WHERE film_id = ?", Integer.class, id)).isEqualTo(1);
        jdbc.update("UPDATE films SET actors = 'Cillian Murphy' WHERE id = ?", id);
        assertThat(direct("Tom Hardy")).isEmpty();
        assertThat(direct("Murphy")).isPresent();
        jdbc.update("DELETE FROM user_films WHERE film_id = ?", id);
        jdbc.update("DELETE FROM films WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film_search_people", Integer.class)).isZero();
    }

    @Test
    void queryAndDatabaseNormalizationAgreeForInternationalNames() {
        for (String name : List.of("İlker Çağrı", "Chloë Sevigny", "Stellan Skarsgård", "Zoë Kravitz", "Björk")) {
            assertThat(SearchText.normalize(name)).isEqualTo(
                    jdbc.queryForObject("SELECT archive_normalize_title(?)", String.class, name));
        }
        long id = add("Dune", 2021, "Sci-Fi", 1, null);
        jdbc.update("UPDATE films SET actors = 'Stellan Skarsgård' WHERE id = ?", id);
        assertThat(titles(direct("Stellan Skarsgård").orElseThrow())).containsExactly("Dune");
    }

    @Test
    void directTitlesTyposAndDescriptionsTakeDifferentPaths() {
        add("Tenet", 2020, "Sci-Fi", 1, null);
        assertThat(direct("Tenet").orElseThrow().mode()).isEqualTo("direct");
        assertThat(titles(direct("Tennet").orElseThrow())).containsExactly("Tenet");
        assertThat(direct("Tennet").orElseThrow().mode()).isEqualTo("spelling");
        assertThat(direct("time moves backwards in a spy mission")).isEmpty();
        add("Alien", 1979, "Sci-Fi", 1, null);
        add("Allen", 2000, "Drama", 1, null);
        assertThat(direct("Alen")).isEmpty(); // Two valid one-edit corrections: do not silently pick one.
    }

    @Test
    void genreAliasesAndPersonFiltersApplyToAllSearchBranches() {
        long a = add("Dunkirk", 2017, "Action, War", 1, 1.0);
        long b = add("Inception", 2010, "Action, Sci-Fi", 1, 1.0);
        jdbc.update("UPDATE films SET actors = 'Tom Hardy', director = 'Christopher Nolan' WHERE id IN (?,?)", a, b);
        jdbc.update("UPDATE films SET embedding_content_hash = search_content_hash WHERE id IN (?,?)", a, b);
        assertThat(titles(direct("savaş").orElseThrow())).containsExactly("Dunkirk");
        assertThat(titles(direct("bilim kurgu").orElseThrow())).containsExactly("Inception");
        var r = request("time and dreams"); r.setActor("Tom Hardy"); r.setDirector("Christopher Nolan");
        r.setGenre("bilim kurgu"); r.setYearTo(2015);
        assertThat(titles(repository.search(1, vector(1), r, PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC)))
                .containsExactly("Inception");
        r.setActor("Hardy"); // The dedicated actor filter is an exact full-name filter.
        assertThat(repository.coverage(1, r).total()).isZero();
    }

    @Test
    void mixedDescriptionsStillRetrievePeopleWithoutMixingUpNames() {
        long id = add("Dunkirk", 2017, "War", 1, null);
        jdbc.update("UPDATE films SET actors = 'Tom Hardy' WHERE id = ?", id);
        assertThat(direct("Tom Hardy war movie")).isEmpty();
        assertThat(titles(search("Tom Hardy war movie"))).contains("Dunkirk");
    }

    @Test
    void candidateBudgetIsVisibleAndAppliedAfterOwnershipAndFilters() {
        for (int i = 0; i < 10; i++) add("Interstellar " + i, 2014, "Adventure", 2, null);
        add("Interstellar", 2014, "Adventure", 1, null);
        repository = new ArchiveSemanticSearchRepository(new NamedParameterJdbcTemplate(jdbc), VECTORS, MODEL, .35, .35, 1);
        assertThat(titles(search("Interstelar"))).containsExactly("Interstellar");
        assertThat(search("Interstelar").spellingCandidatesLimited()).isFalse();
        add("Interstellar two", 2014, "Adventure", 1, null);
        assertThat(search("Interstelar").spellingCandidatesLimited()).isTrue();
        assertThat(direct("Interstellar").orElseThrow().page().getTotalElements()).isEqualTo(1);
    }

    @Test
    void directPagesHaveExactStableTotalsEvenBeyondLastPage() {
        long a = add("Dunkirk", 2017, "War", 1, null);
        long b = add("Tenet", 2020, "Sci-Fi", 1, null);
        jdbc.update("UPDATE films SET director = 'Christopher Nolan' WHERE id IN (?,?)", a, b);
        var r = request("Nolan");
        var result = repository.directSearch(1, r, PageRequest.of(10, 1), ArchiveSortOption.YEAR_ASC).orElseThrow();
        assertThat(result.page().getTotalElements()).isEqualTo(2);
        assertThat(result.page().getContent()).isEmpty();
    }

    @Test
    void metadataQueueDeduplicatesAndStopsAfterThreeIncompleteRefreshes() {
        long id = add("Pending", 2010, "Drama", 1, null);
        Film film = org.mockito.Mockito.mock(Film.class);
        org.mockito.Mockito.when(film.getId()).thenReturn(id);
        var queue = new com.example.film.service.FilmMaintenanceQueue(jdbc, MODEL);
        queue.request(film, false); queue.request(film, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film_metadata_refresh", Integer.class)).isEqualTo(1);
        var discovery = org.mockito.Mockito.mock(com.example.film.service.FilmCatalogDiscoveryService.class);
        org.mockito.Mockito.when(discovery.refreshMetadata(org.mockito.ArgumentMatchers.anyString())).thenReturn(film);
        var worker = new com.example.film.service.FilmMetadataRefreshWorker(jdbc, discovery, 4);
        worker.refresh(); worker.refresh();
        for (int i = 0; i < 3; i++) {
            jdbc.update("UPDATE film_metadata_refresh SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'");
            worker.refresh();
        }
        org.mockito.Mockito.verify(discovery, org.mockito.Mockito.times(3)).refreshMetadata(org.mockito.ArgumentMatchers.anyString());
        assertThat(jdbc.queryForObject("SELECT attempts FROM film_metadata_refresh WHERE film_id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT embedding_requested FROM films WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void successfulMetadataRefreshClearsQueueAndRequestedUnsavedFilmsCanBeIndexed() {
        long id = add("Pending", 2010, "Drama", 1, null);
        jdbc.update("DELETE FROM user_films WHERE film_id = ?", id);
        Film film = org.mockito.Mockito.mock(Film.class);
        org.mockito.Mockito.when(film.getId()).thenReturn(id);
        org.mockito.Mockito.when(film.getActors()).thenReturn("Tom Hardy");
        org.mockito.Mockito.when(film.getDirector()).thenReturn("Christopher Nolan");
        new com.example.film.service.FilmMaintenanceQueue(jdbc, MODEL).request(film, true);
        var discovery = org.mockito.Mockito.mock(com.example.film.service.FilmCatalogDiscoveryService.class);
        org.mockito.Mockito.when(discovery.refreshMetadata(org.mockito.ArgumentMatchers.anyString())).thenReturn(film);
        new com.example.film.service.FilmMetadataRefreshWorker(jdbc, discovery, 4).refresh();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film_metadata_refresh", Integer.class)).isZero();
        var films = org.mockito.Mockito.mock(com.example.film.FilmRepository.class);
        var indexer = org.mockito.Mockito.mock(com.example.search.service.FilmSemanticIndexService.class);
        var embeddings = org.mockito.Mockito.mock(FilmEmbeddingService.class);
        org.mockito.Mockito.when(embeddings.getEmbeddingModelName()).thenReturn(MODEL);
        org.mockito.Mockito.when(films.findAllById(List.of(id))).thenReturn(List.of(film));
        new com.example.search.service.ArchiveEmbeddingRepairWorker(jdbc, films, indexer, embeddings, 16).repair();
        org.mockito.Mockito.verify(indexer).indexFilms(List.of(film));
    }

    java.util.Optional<ArchiveSearchResult> direct(String q) {
        return repository.directSearch(1, request(q), PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "search.benchmark", matches = "true")
    void measureSearchPathsAndInspectRealPlans() throws Exception {
        // Opt-in diagnostic; timings are reported, never used as machine-dependent pass/fail assertions.
        jdbc.update("""
            INSERT INTO films(imdb_id,title,year_text,release_year,type,director,actors,genres_text,embedding,embedding_model,embedding_content_hash)
            SELECT 'tt' || (1000000 + g), title, '2020', 2020, 'movie', director, actors, 'Drama', CAST(? AS vector), ?,
                archive_embedding_hash(archive_embedding_text(title,'2020','movie','Drama',director,actors,NULL,E'\\n'))
            FROM (SELECT g, CASE WHEN g = 1 THEN 'Tenet' ELSE 'Catalog Film ' || g END AS title,
                CASE WHEN g %% 17 = 0 THEN 'Christopher Nolan' ELSE 'Director ' || g END AS director,
                CASE WHEN g %% 19 = 0 THEN 'Tom Hardy' ELSE 'Actor ' || g END AS actors
                FROM generate_series(1, 5000) g) fixture
            """.replace("%%", "%"), VECTORS.toPgVector(vector(.8)), MODEL);
        jdbc.execute("VACUUM (ANALYZE) films"); jdbc.execute("ANALYZE film_search_people");
        try (var connection = jdbc.getDataSource().getConnection()) {
            var ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            class Capture extends NamedParameterJdbcTemplate {
                String sql; java.util.Map<String, ?> parameters;
                Capture() { super(ds); }
                @Override public <T> T query(String sql, java.util.Map<String, ?> parameters,
                        org.springframework.jdbc.core.ResultSetExtractor<T> extractor) {
                    this.sql = sql; this.parameters = parameters;
                    return super.query(sql, parameters, extractor);
                }
            }
            var capture = new Capture();
            repository = new ArchiveSemanticSearchRepository(capture, VECTORS, MODEL, .35, .35);
            for (int size : new int[]{100, 1000, 5000}) {
                jdbc.update("DELETE FROM user_films");
                jdbc.update("INSERT INTO user_films(user_id,film_id) SELECT 1,id FROM films WHERE id <= ?", size);
                jdbc.execute("ANALYZE user_films");
                for (String query : List.of("Tenet", "Nolan", "Tennet", "time moves backwards")) {
                    var r = request(query);
                    Runnable action = query.equals("time moves backwards")
                            ? () -> repository.search(1, vector(1), r, PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC)
                            : () -> repository.directSearch(1, r, PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC);
                    action.run(); action.run();
                    long[] samples = new long[20];
                    for (int i = 0; i < samples.length; i++) {
                        long start = System.nanoTime(); action.run(); samples[i] = System.nanoTime() - start;
                    }
                    java.util.Arrays.sort(samples);
                    System.out.printf(java.util.Locale.ROOT, "SEARCH_BENCH size=%d query=%s p50=%.2fms p95=%.2fms%n",
                            size, query, samples[9] / 1e6, samples[18] / 1e6);
                }
            }
            direct("Nolan");
            var plan = capture.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + capture.sql,
                    capture.parameters, String.class);
            java.nio.file.Files.write(java.nio.file.Path.of("target/search-direct-plan.txt"), plan);
            assertThat(String.join("\n", plan)).contains("idx_search_people_words");
            repository.search(1, vector(1), request("time moves backwards"), PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC);
            java.nio.file.Files.write(java.nio.file.Path.of("target/search-hybrid-plan.txt"),
                    capture.queryForList("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + capture.sql, capture.parameters, String.class));
        }
    }

    long add(String title, int year, String genre, long owner, Double similarity) {
        long next = jdbc.queryForObject("SELECT coalesce(max(id),0)+1 FROM films", Long.class);
        long id = jdbc.queryForObject("""
            INSERT INTO films(imdb_id,title,year_text,release_year,type,genres_text)
            VALUES (?,?,?,?, 'movie', ?) RETURNING id
            """, Long.class, "tt" + String.format("%07d", next), title, "" + year, year, genre);
        jdbc.update("INSERT INTO user_films(user_id,film_id,added_at) VALUES (?,?,?)",
                owner, id, OffsetDateTime.parse("2026-01-01T00:00:00Z").plusDays(id));
        if (similarity != null) jdbc.update("""
            UPDATE films SET embedding = CAST(? AS vector), embedding_model = ?,
                embedding_content_hash = search_content_hash WHERE id = ?
            """, VECTORS.toPgVector(vector(similarity)), MODEL, id);
        return id;
    }

    ArchiveSearchResult search(String query) {
        return repository.search(1, vector(1), request(query), PageRequest.of(0, 20), ArchiveSortOption.RELEVANCE_DESC);
    }
    static ArchiveSearchRequest request(String query) {
        var request = new ArchiveSearchRequest();
        request.setQuery(query);
        return request;
    }
    static List<String> titles(ArchiveSearchResult result) {
        return result.page().map(f -> f.title()).getContent();
    }
}

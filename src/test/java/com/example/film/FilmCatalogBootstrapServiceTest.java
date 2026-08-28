package com.example.film;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.film.service.FilmCatalogBootstrapService;
import com.example.film.service.FilmCatalogBootstrapService.BootstrapReport;
import com.example.film.service.FilmCatalogDiscoveryService;
import com.example.search.service.FilmSemanticIndexService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmCatalogBootstrapServiceTest {

    private static final String BASICS_HEADER =
            "tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult"
                    + "\tstartYear\tendYear\truntimeMinutes\tgenres\n";
    private static final String RATINGS_HEADER =
            "tconst\taverageRating\tnumVotes\n";

    @TempDir
    Path tempDir;

    @Mock
    FilmRepository filmRepository;
    @Mock
    FilmCatalogDiscoveryService discoveryService;
    @Mock
    FilmSemanticIndexService semanticIndexService;

    Path basics;
    Path ratings;

    @BeforeEach
    void setUp() throws IOException {
        basics = writeGzip("title.basics.tsv.gz", BASICS_HEADER
                + movie("tt0000001", 1900)
                + movie("tt0000002", 1994)
                + movie("tt0000003", 2010)
                + movie("tt0000004", 2024));
        ratings = writeGzip("title.ratings.tsv.gz", RATINGS_HEADER
                + "tt0000001\t9.9\t10\n"
                + "tt0000002\t9.3\t3000000\n"
                + "tt0000003\t8.8\t2500000\n"
                + "tt0000004\t7.0\t500000\n");
    }

    @Test
    void importsMostVotedMoviesBeforeApplyingLimit() throws IOException {
        BootstrapReport report = bootstrap(service(2, 20));

        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).getOrFetchFilm("tt0000002");
        order.verify(discoveryService).getOrFetchFilm("tt0000003");
        verifyNoMoreInteractions(discoveryService);
        verify(filmRepository, never()).existsByImdbId("tt0000001");
        assertThat(report.scannedTitles()).isEqualTo(4);
        assertThat(report.eligibleTitles()).isEqualTo(4);
        assertThat(report.importedFilms()).isEqualTo(2);
        assertThat(report.failedFilms()).isZero();
        assertThat(report.sourceImportAborted()).isFalse();
    }

    @Test
    void breaksVoteTiesByRatingThenImdbIdRegardlessOfFileOrder() throws IOException {
        writeGzip("title.ratings.tsv.gz", RATINGS_HEADER
                + "tt0000004\t8.0\t1000\n"
                + "tt0000001\t7.0\t1000\n"
                + "tt0000003\t9.0\t1000\n"
                + "tt0000002\t8.0\t1000\n");

        bootstrap(service(4, 20));

        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).getOrFetchFilm("tt0000003");
        order.verify(discoveryService).getOrFetchFilm("tt0000002");
        order.verify(discoveryService).getOrFetchFilm("tt0000004");
        order.verify(discoveryService).getOrFetchFilm("tt0000001");
        verifyNoMoreInteractions(discoveryService);
    }

    @Test
    void skipsExistingMoviesWithoutUsingNewMovieQuota() throws IOException {
        when(filmRepository.existsByImdbId("tt0000002")).thenReturn(true);

        BootstrapReport report = bootstrap(service(2, 20));

        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).getOrFetchFilm("tt0000003");
        order.verify(discoveryService).getOrFetchFilm("tt0000004");
        verifyNoMoreInteractions(discoveryService);
        assertThat(report.alreadyPresent()).isEqualTo(1);
        assertThat(report.importedFilms()).isEqualTo(2);
    }

    @Test
    void resumesWithNextMostPopularMovieOnNextRun() throws IOException {
        FilmCatalogBootstrapService service = service(1, 20);
        bootstrap(service);
        when(filmRepository.existsByImdbId("tt0000002")).thenReturn(true);

        BootstrapReport report = bootstrap(service);

        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).getOrFetchFilm("tt0000002");
        order.verify(discoveryService).getOrFetchFilm("tt0000003");
        verifyNoMoreInteractions(discoveryService);
        assertThat(report.importedFilms()).isEqualTo(1);
        assertThat(report.alreadyPresent()).isEqualTo(1);
    }

    @Test
    void failuresDoNotUseQuotaAndSuccessResetsConsecutiveFailureCount() throws IOException {
        when(discoveryService.getOrFetchFilm("tt0000002"))
                .thenThrow(new IllegalStateException("Unavailable"));
        when(discoveryService.getOrFetchFilm("tt0000003"))
                .thenReturn(new Film());
        when(discoveryService.getOrFetchFilm("tt0000004"))
                .thenThrow(new IllegalStateException("Unavailable"));
        when(discoveryService.getOrFetchFilm("tt0000001"))
                .thenReturn(new Film());

        BootstrapReport report = bootstrap(service(2, 2));

        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).getOrFetchFilm("tt0000002");
        order.verify(discoveryService).getOrFetchFilm("tt0000003");
        order.verify(discoveryService).getOrFetchFilm("tt0000004");
        order.verify(discoveryService).getOrFetchFilm("tt0000001");
        assertThat(report.importedFilms()).isEqualTo(2);
        assertThat(report.failedFilms()).isEqualTo(2);
        assertThat(report.sourceImportAborted()).isFalse();
        assertThat(report.failureSamples())
                .extracting(FilmCatalogBootstrapService.ImportFailure::imdbId)
                .containsExactly("tt0000002", "tt0000004");
    }

    @Test
    void stopsAfterConsecutiveFailuresAndStillBackfillsEmbeddings() throws IOException {
        when(discoveryService.getOrFetchFilm("tt0000002"))
                .thenThrow(new IllegalStateException("Quota exceeded"));
        when(discoveryService.getOrFetchFilm("tt0000003"))
                .thenThrow(new IllegalStateException("Quota exceeded"));
        Film film = new Film();
        when(filmRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(film)));
        when(semanticIndexService.indexFilms(List.of(film))).thenReturn(1);

        BootstrapReport report = service(4, 2).bootstrap(basics);

        verify(discoveryService, never()).getOrFetchFilm("tt0000004");
        verify(discoveryService, never()).getOrFetchFilm("tt0000001");
        assertThat(report.sourceImportAborted()).isTrue();
        assertThat(report.failedFilms()).isEqualTo(2);
        assertThat(report.importedFilms()).isZero();
        assertThat(report.generatedEmbeddings()).isEqualTo(1);
    }

    @Test
    void excludesAdultNonMovieUnratedAndMalformedTitlesAndDeduplicates() throws IOException {
        writeGzip("title.basics.tsv.gz", BASICS_HEADER
                + movie("tt0000001", 1900)
                + movie("tt0000002", 1994)
                + movie("tt0000002", 1994)
                + "tt0000003\tmovie\tAdult\tAdult\t1\t2020\t\\N\t90\tDrama\n"
                + "tt0000004\ttvSeries\tShow\tShow\t0\t2020\t\\N\t90\tDrama\n"
                + "tt0000005\tshort\tShort\tShort\t0\t2020\t\\N\t10\tDrama\n"
                + movie("invalid", 2020)
                + "bad row\n\n");
        writeGzip("title.ratings.tsv.gz", RATINGS_HEADER
                + "tt0000002\t9.3\t1000\n"
                + "tt0000002\t9.3\t1000\n"
                + "tt0000003\t9.9\t9000000\n"
                + "tt0000004\t9.9\t9000000\n"
                + "tt0000005\t9.9\t9000000\n"
                + "tt9999999\t9.9\t9000000\n"
                + "invalid\t9.9\t9000000\n");

        BootstrapReport report = bootstrap(service(20, 20));

        verify(discoveryService).getOrFetchFilm("tt0000002");
        verifyNoMoreInteractions(discoveryService);
        assertThat(report.eligibleTitles()).isEqualTo(2);
        assertThat(report.importedFilms()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\\N\t1000", "NaN\t1000", "Infinity\t1000", "11\t1000", "0\t1000",
            "9.0\t\\N", "9.0\t-1", "9.0\t0", "9.0", "9.0\t1000\textra"
    })
    void skipsInvalidRatingsWithoutFallingBackToFileOrder(String values) throws IOException {
        writeGzip("title.ratings.tsv.gz", RATINGS_HEADER
                + "tt0000001\t" + values + "\n"
                + "tt0000003\t8.8\t1000\n");

        bootstrap(service(20, 20));

        verify(discoveryService).getOrFetchFilm("tt0000003");
        verifyNoMoreInteractions(discoveryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "wrong header\n", "tconst\taverageRating\tnumVotes\n",
            "tconst\taverageRating\tnumVotes\ntt9999999\t8.0\t1000\n"
    })
    void rejectsUnusableRatingsBeforeAnyImports(String data) throws IOException {
        writeGzip("title.ratings.tsv.gz", data);

        assertThatThrownBy(() -> service(2, 20).bootstrap(basics))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(filmRepository, discoveryService, semanticIndexService);
    }

    @Test
    void missingRatingsDoesNotImportOldMoviesAndReleasesRunLock() throws IOException {
        FilmCatalogBootstrapService service = service(1, 20);
        Files.delete(ratings);

        assertThatThrownBy(() -> service.bootstrap(basics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be found");
        verifyNoInteractions(filmRepository, discoveryService, semanticIndexService);

        writeGzip("title.ratings.tsv.gz", RATINGS_HEADER
                + "tt0000003\t8.8\t1000\n");
        assertThat(bootstrap(service).importedFilms()).isEqualTo(1);
        verify(discoveryService).getOrFetchFilm("tt0000003");
    }

    @Test
    void rejectsCorruptGzipBeforeAnyImports() throws IOException {
        Files.writeString(ratings, "not gzip");

        assertThatThrownBy(() -> service(2, 20).bootstrap(basics))
                .isInstanceOf(IOException.class);
        verifyNoInteractions(filmRepository, discoveryService, semanticIndexService);
    }

    @Test
    void rejectsInvalidBasicsHeaderBeforeAnyImports() throws IOException {
        writeGzip("title.basics.tsv.gz", "wrong header\n");

        assertThatThrownBy(() -> service(2, 20).bootstrap(basics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title.basics");
        verifyNoInteractions(filmRepository, discoveryService, semanticIndexService);
    }

    private FilmCatalogBootstrapService service(int maxFilms, int maxFailures) {
        return new FilmCatalogBootstrapService(
                filmRepository, discoveryService, semanticIndexService,
                maxFilms, 64, maxFailures, Duration.ZERO, ratings.toString()
        );
    }

    private BootstrapReport bootstrap(FilmCatalogBootstrapService service) throws IOException {
        when(filmRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        return service.bootstrap(basics);
    }

    private Path writeGzip(String name, String data) throws IOException {
        Path path = tempDir.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private String movie(String imdbId, int year) {
        return imdbId + "\tmovie\tFilm\tFilm\t0\t" + year
                + "\t\\N\t120\tDrama\n";
    }
}

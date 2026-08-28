package com.example.film.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.search.service.FilmSemanticIndexService;

/* FUNCTONALITY
* Do not keep database transactions open during network calls.
* The same operation must be repeatable.Movies already present in the database are not re-downloaded.
* Import rated movies by IMDb vote count, not by title.basics file order.
* Missing or outdated embeddings are completed in bulk at the end of the process.
* A consecutive error limit is triggered when API quota is exceeded.
*/

@Service
public class FilmCatalogBootstrapService {

    private static final Logger log =
            LoggerFactory.getLogger(FilmCatalogBootstrapService.class);

    private static final Pattern IMDB_ID_PATTERN =
            Pattern.compile("^tt\\d+$");

    private static final Set<String> SUPPORTED_TITLE_TYPES =
            Set.of("movie");

    private static final Comparator<PopularTitle> POPULARITY_ORDER =
            Comparator.comparingLong(PopularTitle::votes).reversed()
                    .thenComparing(
                            Comparator.comparingDouble(
                                    PopularTitle::rating
                            ).reversed()
                    )
                    .thenComparing(PopularTitle::imdbId);

    private static final int MAX_REPORTED_FAILURES = 100;

    private final FilmRepository filmRepository;
    private final FilmCatalogDiscoveryService discoveryService;
    private final FilmSemanticIndexService semanticIndexService;

    private final int maxFilms;
    private final int embeddingBatchSize;
    private final int maxConsecutiveFailures;
    private final Duration requestDelay;
    private final String ratingsPath;


    // Prevents to run 2 boostrap processes synchronously.
    private final AtomicBoolean bootstrapRunning =
            new AtomicBoolean(false);

    public FilmCatalogBootstrapService(
            FilmRepository filmRepository,
            FilmCatalogDiscoveryService discoveryService,
            FilmSemanticIndexService semanticIndexService,
            @Value("${app.catalog.bootstrap.max-films:50000}")
            int maxFilms,
            @Value("${app.semantic.embedding-batch-size:64}")
            int embeddingBatchSize,
            @Value("${app.catalog.bootstrap.max-consecutive-failures:20}")
            int maxConsecutiveFailures,
            @Value("${app.catalog.bootstrap.request-delay:250ms}")
            Duration requestDelay,
            @Value("${app.catalog.bootstrap.ratings-path:https://datasets.imdbws.com/title.ratings.tsv.gz}")
            String ratingsPath
    ) {
        this.filmRepository = filmRepository;
        this.discoveryService = discoveryService;
        this.semanticIndexService = semanticIndexService;
        this.ratingsPath = ratingsPath;

        this.maxFilms = Math.max(1, maxFilms);
        this.embeddingBatchSize =
                Math.max(1, Math.min(embeddingBatchSize, 256));
        this.maxConsecutiveFailures =
                Math.max(1, maxConsecutiveFailures);

        this.requestDelay =
                requestDelay == null || requestDelay.isNegative()
                        ? Duration.ZERO
                        : requestDelay;
    }


    public BootstrapReport bootstrap(
            Path titleBasicsGzip
    ) throws IOException {

        validateDataset(titleBasicsGzip);

        if (!bootstrapRunning.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Film catalog bootstrap is already running."
            );
        }

        Instant startedAt = Instant.now();

        long alreadyPresent = 0;
        long imported = 0;
        long failed = 0;

        int consecutiveFailures = 0;
        boolean sourceImportAborted = false;

        List<ImportFailure> failureSamples =
                new ArrayList<>();

        try {
            PopularTitles source = loadPopularTitles(titleBasicsGzip);

            log.info(
                    "Film catalog import ordered by IMDb vote count. "
                            + "Ranked movies: {}, import limit: {}",
                    source.titles().size(),
                    maxFilms
            );

            for (PopularTitle title : source.titles()) {
                if (imported >= maxFilms) {
                    break;
                }

                if (filmRepository.existsByImdbId(title.imdbId())) {
                    alreadyPresent++;
                    continue;
                }

                try {

                    discoveryService.getOrFetchFilm(
                            title.imdbId()
                    );

                    imported++;
                    consecutiveFailures = 0;

                    if (imported % 100 == 0) {
                        log.info(
                                "Film catalog bootstrap progress. "
                                        + "Imported: {}, scanned: {}",
                                imported,
                                source.scanned()
                        );
                    }

                } catch (RuntimeException exception) {
                    failed++;
                    consecutiveFailures++;

                    log.warn(
                            "Film import failed for IMDb ID: {}",
                            title.imdbId(),
                            exception
                    );

                    addFailureSample(
                            failureSamples,
                            title.imdbId(),
                            exception
                    );

                    if (consecutiveFailures
                            >= maxConsecutiveFailures) {

                        sourceImportAborted = true;

                        log.error(
                                "Catalog source import stopped after {} "
                                        + "consecutive failures.",
                                consecutiveFailures
                        );

                        break;
                    }
                }

                waitForRateLimit();
            }

            int generatedEmbeddings =
                    indexMissingEmbeddings();

            Duration elapsed =
                    Duration.between(
                            startedAt,
                            Instant.now()
                    );

            return new BootstrapReport(
                    source.scanned(),
                    source.eligible(),
                    alreadyPresent,
                    imported,
                    failed,
                    generatedEmbeddings,
                    sourceImportAborted,
                    elapsed,
                    List.copyOf(failureSamples)
            );

        } finally {
            bootstrapRunning.set(false);
        }
    }

    public FilmDetail getDetailAndEnsureCataloged(
            String imdbId
    ) {
        return discoveryService.getFilmDetail(imdbId);
    }

    private PopularTitles loadPopularTitles(
            Path titleBasicsGzip
    ) throws IOException {
        log.info("Preparing IMDb movie popularity order before importing.");

        long scanned = 0;
        Set<String> eligibleIds = new HashSet<>();

        // Stream the large basics file; retain only eligible movie IDs.
        try (InputStream input = Files.newInputStream(titleBasicsGzip);
                BufferedReader reader = gzipReader(input)) {
            validateHeader(reader.readLine());

            String line;
            while ((line = reader.readLine()) != null) {
                scanned++;
                DatasetTitle title = parseTitle(line);
                if (title != null && isEligible(title)) {
                    eligibleIds.add(title.imdbId());
                }
            }
        }

        long eligible = eligibleIds.size();
        List<PopularTitle> titles = new ArrayList<>();

        try (InputStream input = openRatingsDataset();
                BufferedReader reader = gzipReader(input)) {
            if (!"tconst\taverageRating\tnumVotes".equals(reader.readLine())) {
                throw new IllegalArgumentException(
                        "Unsupported IMDb title.ratings dataset."
                );
            }

            String line;
            while ((line = reader.readLine()) != null) {
                PopularTitle title = parseRating(line);
                if (title != null && eligibleIds.remove(title.imdbId())) {
                    titles.add(title);
                }
            }
        }

        // Never fall back to file order if popularity data is unavailable.
        if (eligible > 0 && titles.isEmpty()) {
            throw new IllegalArgumentException(
                    "IMDb ratings dataset contains no rated eligible movies."
            );
        }

        titles.sort(POPULARITY_ORDER);
        return new PopularTitles(scanned, eligible, titles);
    }

    private InputStream openRatingsDataset() throws IOException {
        if (ratingsPath == null || ratingsPath.isBlank()) {
            throw new IllegalArgumentException(
                    "IMDb ratings dataset path cannot be empty."
            );
        }

        if (ratingsPath.startsWith("https://")) {
            URLConnection connection = URI.create(ratingsPath)
                    .toURL()
                    .openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            return connection.getInputStream();
        }

        Path dataset = Path.of(ratingsPath);
        validateDataset(dataset);
        return Files.newInputStream(dataset);
    }

    private BufferedReader gzipReader(InputStream input) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(input),
                        StandardCharsets.UTF_8
                ),
                64 * 1024
        );
    }

    private PopularTitle parseRating(String line) {
        String[] columns = line.split("\t", -1);
        if (columns.length != 3) {
            return null;
        }

        try {
            String imdbId = columns[0].trim();
            double rating = Double.parseDouble(columns[1].trim());
            long votes = Long.parseLong(columns[2].trim());

            if (!IMDB_ID_PATTERN.matcher(imdbId).matches()
                    || !Double.isFinite(rating)
                    || rating < 1 || rating > 10 || votes <= 0) {
                return null;
            }

            return new PopularTitle(imdbId, votes, rating);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int indexMissingEmbeddings() {
        int pageNumber = 0;
        int generatedCount = 0;

        Page<Film> page;

        do {
            page = filmRepository.findAll(
                    PageRequest.of(
                            pageNumber,
                            embeddingBatchSize,
                            Sort.by(
                                    Sort.Direction.ASC,
                                    "id"
                            )
                    )
            );

            if (!page.getContent().isEmpty()) {
                generatedCount +=
                        semanticIndexService.indexFilms(
                                page.getContent()
                        );
            }

            pageNumber++;

        } while (page.hasNext());

        return generatedCount;
    }

    private DatasetTitle parseTitle(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] columns =
                line.split("\t", -1);

        if (columns.length < 5) {
            return null;
        }

        return new DatasetTitle(
                columns[0].trim(),
                columns[1].trim(),
                "1".equals(columns[4].trim())
        );
    }

    private boolean isEligible(
            DatasetTitle title
    ) {
        return IMDB_ID_PATTERN
                .matcher(title.imdbId())
                .matches()
                && !title.adult()
                && SUPPORTED_TITLE_TYPES.contains(
                        title.titleType()
                );
    }

    private void waitForRateLimit() {
        long delayMillis =
                requestDelay.toMillis();

        if (delayMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMillis);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Film catalog bootstrap was interrupted.",
                    exception
            );
        }
    }

    private void validateDataset(
            Path dataset
    ) {
        if (dataset == null) {
            throw new IllegalArgumentException(
                    "IMDb dataset path cannot be null."
            );
        }

        if (!Files.isRegularFile(dataset)) {
            throw new IllegalArgumentException(
                    "IMDb dataset could not be found: "
                            + dataset
            );
        }

        if (!Files.isReadable(dataset)) {
            throw new IllegalArgumentException(
                    "IMDb dataset is not readable: "
                            + dataset
            );
        }
    }

    private void validateHeader(
            String header
    ) {
        if (header == null
                || !header.startsWith(
                        "tconst\ttitleType"
                )) {

            throw new IllegalArgumentException(
                    "Unsupported IMDb title.basics dataset."
            );
        }
    }

    private void addFailureSample(
            List<ImportFailure> failures,
            String imdbId,
            RuntimeException exception
    ) {
        if (failures.size() >= MAX_REPORTED_FAILURES) {
            return;
        }

        String message =
                exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();

        failures.add(
                new ImportFailure(
                        imdbId,
                        message
                )
        );
    }

    private record DatasetTitle(
            String imdbId,
            String titleType,
            boolean adult
    ) {
    }

    private record PopularTitle(String imdbId, long votes, double rating) {
    }

    private record PopularTitles(
            long scanned,
            long eligible,
            List<PopularTitle> titles
    ) {
    }

    public record ImportFailure(
            String imdbId,
            String reason
    ) {
    }

    public record BootstrapReport(
            long scannedTitles,
            long eligibleTitles,
            long alreadyPresent,
            long importedFilms,
            long failedFilms,
            int generatedEmbeddings,
            boolean sourceImportAborted,
            Duration elapsed,
            List<ImportFailure> failureSamples
    ) {
    }
}

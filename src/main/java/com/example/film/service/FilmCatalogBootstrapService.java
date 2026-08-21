package com.example.film.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
* Movies already present in the database are not re-downloaded.
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
            Set.of(
                    "movie",
                    "Series"
            );

    private static final int MAX_REPORTED_FAILURES = 100;

    private final FilmRepository filmRepository;
    private final FilmCatalogDiscoveryService discoveryService;
    private final FilmSemanticIndexService semanticIndexService;

    private final int maxFilms;
    private final int embeddingBatchSize;
    private final int maxConsecutiveFailures;
    private final Duration requestDelay;


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
            Duration requestDelay
    ) {
        this.filmRepository = filmRepository;
        this.discoveryService = discoveryService;
        this.semanticIndexService = semanticIndexService;

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

        long scanned = 0;
        long eligible = 0;
        long alreadyPresent = 0;
        long imported = 0;
        long failed = 0;

        int consecutiveFailures = 0;
        boolean sourceImportAborted = false;

        List<ImportFailure> failureSamples =
                new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                new GZIPInputStream(
                                        Files.newInputStream(
                                                titleBasicsGzip
                                        )
                                ),
                                StandardCharsets.UTF_8
                        ),
                        64 * 1024
                )
        ) {
            validateHeader(reader.readLine());

            String line;

            while ((line = reader.readLine()) != null
                    && imported < maxFilms) {

                scanned++;

                DatasetTitle title = parseTitle(line);

                if (title == null || !isEligible(title)) {
                    continue;
                }

                eligible++;

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
                                scanned
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
                    scanned,
                    eligible,
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
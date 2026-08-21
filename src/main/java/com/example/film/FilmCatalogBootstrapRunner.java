package com.example.film;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.film.service.FilmCatalogBootstrapService;
import com.example.film.service.FilmCatalogBootstrapService.BootstrapReport;

@Component
@ConditionalOnProperty(
        name = "app.catalog.bootstrap.enabled",
        havingValue = "true"
)
public class FilmCatalogBootstrapRunner
        implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FilmCatalogBootstrapRunner.class
            );

    private final FilmCatalogBootstrapService bootstrapService;
    private final String datasetPath;

    public FilmCatalogBootstrapRunner(
            FilmCatalogBootstrapService bootstrapService,
            @Value("${app.catalog.bootstrap.dataset-path}")
            String datasetPath
    ) {
        this.bootstrapService = bootstrapService;
        this.datasetPath = datasetPath;
    }

    @Override
    public void run(
            ApplicationArguments arguments
    ) {

        Thread.ofVirtual()
                .name("film-catalog-bootstrap")
                .start(this::runBootstrap);
    }

    private void runBootstrap() {
        try {
            Path dataset = Path.of(datasetPath)
                    .toAbsolutePath()
                    .normalize();

            BootstrapReport report =
                    bootstrapService.bootstrap(dataset);
            log.info(
                    """
                    Film catalog bootstrap completed.
                    Scanned titles: {}
                    Eligible titles: {}
                    Already present: {}
                    Imported films: {}
                    Failed films: {}
                    Generated embeddings: {}
                    Source import aborted: {}
                    Elapsed: {}
                    """,
                    report.scannedTitles(),
                    report.eligibleTitles(),
                    report.alreadyPresent(),
                    report.importedFilms(),
                    report.failedFilms(),
                    report.generatedEmbeddings(),
                    report.sourceImportAborted(),
                    report.elapsed()
            );

            report.failureSamples().forEach(
                    failure -> log.warn(
                            "Film import failure. IMDb ID: {}, reason: {}",
                            failure.imdbId(),
                            failure.reason()
                    )
            );

        } catch (Exception exception) {
            log.error(
                    "Film catalog bootstrap could not be completed.",
                    exception
            );
        }
    }
}
package com.example.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.search.service.FilmCatalogRefreshService;

@Component
@ConditionalOnProperty(
        name = "app.catalog.refresh-on-startup",
        havingValue = "true"
)
public class FilmCatalogRefreshRunner
        implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FilmCatalogRefreshRunner.class
            );

    private final FilmCatalogRefreshService
            refreshService;


    public FilmCatalogRefreshRunner(
            FilmCatalogRefreshService refreshService
    ) {

        this.refreshService =
                refreshService;
    }


    @Override
    public void run(
            ApplicationArguments arguments
    ) {

        int refreshedCount =
                refreshService.refreshAll();

        log.info(
                "Film catalog refresh completed. Refreshed films: {}",
                refreshedCount
        );
    }
}
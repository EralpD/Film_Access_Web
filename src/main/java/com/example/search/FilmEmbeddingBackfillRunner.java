package com.example.search;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.search.service.FilmEmbeddingBackfillService;

@Component
@ConditionalOnProperty(
        name = "app.semantic.backfill-on-startup",
        havingValue = "true"
)
public class FilmEmbeddingBackfillRunner
        implements ApplicationRunner {

    private final FilmEmbeddingBackfillService
            backfillService;


    public FilmEmbeddingBackfillRunner(
            FilmEmbeddingBackfillService backfillService
    ) {
        this.backfillService = backfillService;
    }


    @Override
    public void run(
            ApplicationArguments args
    ) {

        int indexed =
                backfillService
                        .indexExistingFilms();

        System.out.println(
                "Semantic embedding backfill completed. "
                + "Indexed films: "
                + indexed
        );
    }
}

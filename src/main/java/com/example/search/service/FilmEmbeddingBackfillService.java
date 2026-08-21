package com.example.search.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.film.Film;
import com.example.film.FilmRepository;

@Service
public class FilmEmbeddingBackfillService {

    private final FilmRepository filmRepository;

    private final FilmSemanticIndexService
            filmSemanticIndexService;

    private final int batchSize;


    public FilmEmbeddingBackfillService(
            FilmRepository filmRepository,
            FilmSemanticIndexService filmSemanticIndexService,
            @Value("${app.semantic.embedding-batch-size:64}")
            int batchSize
    ) {
        this.filmRepository = filmRepository;
        this.filmSemanticIndexService =
                filmSemanticIndexService;
        this.batchSize = Math.max(1, Math.min(batchSize, 256));
    }

    public int indexExistingFilms() {

        List<Film> films =
                filmRepository.findAll();

        int indexedCount = 0;

        for (int start = 0;
                start < films.size();
                start += batchSize) {

            int end = Math.min(
                    start + batchSize,
                    films.size()
            );

            indexedCount += filmSemanticIndexService
                    .indexFilms(films.subList(start, end));
        }

        return indexedCount;
    }


}


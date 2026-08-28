package com.example.search.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

        int indexedCount = 0;
        int page = 0;
        org.springframework.data.domain.Page<Film> films;
        do {
            films = filmRepository.findAll(PageRequest.of(page++, batchSize, Sort.by("id")));
            indexedCount += filmSemanticIndexService.indexFilms(films.getContent());
        } while (films.hasNext());

        return indexedCount;
    }


}


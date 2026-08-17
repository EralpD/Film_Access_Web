package com.example.search.service;

import org.springframework.stereotype.Service;

import com.example.film.Film;
import com.example.search.FilmEmbeddingRepository;



@Service
public class FilmSemanticIndexService {

    private final FilmEmbeddingService embeddingService;

    private final FilmEmbeddingRepository embeddingRepository;


    public FilmSemanticIndexService(
            FilmEmbeddingService embeddingService,
            FilmEmbeddingRepository embeddingRepository
    ) {
        this.embeddingService = embeddingService;
        this.embeddingRepository =
                embeddingRepository;
    }


    public boolean indexFilm(Film film) {

        if (film.getId() == null) {
            throw new IllegalArgumentException(
                    "Film must be persisted before indexing."
            );
        }

        if (embeddingRepository.hasEmbedding(
                film.getId()
        )) {
            return false;
        }

        float[] embedding =
                embeddingService.createEmbedding(
                        film
                );

        embeddingRepository.updateEmbedding(
                film.getId(),
                embedding
        );

        return true;
    }

}

package com.example.search.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.film.Film;
import com.example.film.FilmRepository;

@Service
public class FilmEmbeddingBackfillService {

    private final FilmRepository filmRepository;

    private final FilmSemanticIndexService
            filmSemanticIndexService;


    public FilmEmbeddingBackfillService(
            FilmRepository filmRepository,
            FilmSemanticIndexService filmSemanticIndexService
    ) {
        this.filmRepository = filmRepository;
        this.filmSemanticIndexService =
                filmSemanticIndexService;
    }

    public int indexExistingFilms() {

        List<Film> films =
                filmRepository.findAll();

        int indexedCount = 0;

        for (Film film : films) {

            boolean indexed =
                    filmSemanticIndexService
                            .indexFilm(film);

            if (indexed) {
                indexedCount++;
            }
        }

        return indexedCount;
    }


}


package com.example.film.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.archive.mapper.FilmCatalogMapper;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.search.service.FilmSemanticIndexService;

@Service
public class FilmCatalogDiscoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FilmCatalogDiscoveryService.class
            );

    private final FilmRepository filmRepository;

    private final FilmCatalogMapper filmCatalogMapper;

    private final FilmSemanticIndexService
            semanticIndexService;


    public FilmCatalogDiscoveryService(
            FilmRepository filmRepository,
            FilmCatalogMapper filmCatalogMapper,
            FilmSemanticIndexService semanticIndexService
    ) {

        this.filmRepository =
                filmRepository;

        this.filmCatalogMapper =
                filmCatalogMapper;

        this.semanticIndexService =
                semanticIndexService;
    }


    public Film catalogViewedFilm(
            FilmDetail detail
    ) {

        if (detail == null) {

            throw new IllegalArgumentException(
                    "Film detail cannot be null."
            );
        }

        if (detail.getImdbId() == null
                || detail.getImdbId().isBlank()) {

            throw new IllegalArgumentException(
                    "IMDb ID cannot be empty."
            );
        }


        Optional<Film> existingFilm =
                filmRepository.findByImdbId(
                        detail.getImdbId()
                );


        Film savedFilm;

        boolean semanticContentChanged;


        if (existingFilm.isPresent()) {

            Film film =
                    existingFilm.get();

            semanticContentChanged =
                    filmCatalogMapper.updateFilm(
                            film,
                            detail
                    );

            savedFilm =
                    filmRepository.saveAndFlush(
                            film
                    );

        } else {

            Film newFilm =
                    filmCatalogMapper.toFilm(
                            detail
                    );

            savedFilm =
                    filmRepository.saveAndFlush(
                            newFilm
                    );

            semanticContentChanged = false;
        }


        /*
         * Embedding hatası film detay sayfasının açılmasını
         * engellememeli. Embedding yoksa sonraki görüntülemede
         * tekrar denenir.
         */
        try {

            if (semanticContentChanged) {

                semanticIndexService.reindexFilm(
                        savedFilm
                );

            } else {

                semanticIndexService.indexFilm(
                        savedFilm
                );
            }

        } catch (RuntimeException exception) {

            log.warn(
                    "Embedding could not be generated for viewed film: {}",
                    detail.getImdbId(),
                    exception
            );
        }


        return savedFilm;
    }
}
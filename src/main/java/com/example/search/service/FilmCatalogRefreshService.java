package com.example.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.archive.mapper.FilmCatalogMapper;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;

@Service
public class FilmCatalogRefreshService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FilmCatalogRefreshService.class
            );

    private final FilmRepository filmRepository;

    private final OmdbFilmDetailService filmDetailService;

    private final FilmCatalogMapper filmCatalogMapper;

    private final FilmSemanticIndexService
            semanticIndexService;


    public FilmCatalogRefreshService(
            FilmRepository filmRepository,
            OmdbFilmDetailService filmDetailService,
            FilmCatalogMapper filmCatalogMapper,
            FilmSemanticIndexService semanticIndexService
    ) {

        this.filmRepository =
                filmRepository;

        this.filmDetailService =
                filmDetailService;

        this.filmCatalogMapper =
                filmCatalogMapper;

        this.semanticIndexService =
                semanticIndexService;
    }


    public int refreshAll() {

        int refreshedCount = 0;

        for (Film film : filmRepository.findAll()) {

            try {

                FilmDetail detail =
                        filmDetailService.getFilmDetail(
                                film.getImdbId()
                        );

                filmCatalogMapper.updateFilm(
                        film,
                        detail
                );

                Film savedFilm =
                        filmRepository.save(
                                film
                        );

                semanticIndexService.reindexFilm(
                        savedFilm
                );

                refreshedCount++;

            } catch (RuntimeException exception) {

                log.warn(
                        "Film catalog refresh failed for IMDb ID: {}",
                        film.getImdbId(),
                        exception
                );
            }
        }

        return refreshedCount;
    }
}
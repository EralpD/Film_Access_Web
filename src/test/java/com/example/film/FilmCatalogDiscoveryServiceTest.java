package com.example.film;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.archive.mapper.FilmCatalogMapper;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.search.service.FilmSemanticIndexService;
import com.example.film.service.FilmCatalogDiscoveryService;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmCatalogDiscoveryServiceTest {

    @Mock
    private FilmRepository filmRepository;

    @Mock
    private FilmCatalogMapper filmCatalogMapper;

    @Mock
    private FilmSemanticIndexService
            semanticIndexService;

    @Mock
    private OmdbFilmDetailService filmDetailService;

    @Mock
    private FilmDetail detail;

    @Mock
    private Film film;


    private FilmCatalogDiscoveryService service;


    @BeforeEach
    void setUp() {

        service =
                new FilmCatalogDiscoveryService(
                        filmRepository,
                        filmCatalogMapper,
                        semanticIndexService,
                        filmDetailService,
                        Duration.ofDays(30)
                );
    }


    @Test
    void shouldPersistAndIndexPreviouslyUnknownFilm() {

        when(
                detail.getImdbId()
        ).thenReturn(
                "tt1375666"
        );

        when(
                filmRepository.findByImdbId(
                        "tt1375666"
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                filmCatalogMapper.toFilm(
                        detail
                )
        ).thenReturn(
                film
        );

        when(
                filmRepository.saveAndFlush(
                        film
                )
        ).thenReturn(
                film
        );


        Film result =
                service.catalogViewedFilm(
                        detail
                );


        assertSame(
                film,
                result
        );

        verify(
                filmCatalogMapper
        ).toFilm(
                detail
        );

        verify(
                filmRepository
        ).saveAndFlush(
                film
        );

        verify(
                semanticIndexService
        ).indexFilm(
                film
        );
    }


    @Test
    void shouldUpdateAndReindexChangedExistingFilm() {

        when(
                detail.getImdbId()
        ).thenReturn(
                "tt1375666"
        );

        when(
                filmRepository.findByImdbId(
                        "tt1375666"
                )
        ).thenReturn(
                Optional.of(
                        film
                )
        );

        when(
                filmCatalogMapper.updateFilm(
                        film,
                        detail
                )
        ).thenReturn(
                true
        );

        when(
                filmRepository.saveAndFlush(
                        film
                )
        ).thenReturn(
                film
        );


        service.catalogViewedFilm(
                detail
        );


        verify(semanticIndexService).indexFilm(film);
    }


    @Test
    void shouldUseFreshLocalDetailWithoutCallingOmdb() {

        FilmDetail cachedDetail =
                org.mockito.Mockito.mock(FilmDetail.class);

        when(film.getFetchedAt())
                .thenReturn(OffsetDateTime.now());

        when(filmRepository.findByImdbId("tt1375666"))
                .thenReturn(Optional.of(film));

        when(filmCatalogMapper.toFilmDetail(film))
                .thenReturn(cachedDetail);

        FilmDetail result = service.getFilmDetail("tt1375666");

        assertSame(cachedDetail, result);
        verify(filmDetailService, never())
                .getFilmDetail("tt1375666");
        verify(semanticIndexService).indexFilm(film);
    }
}

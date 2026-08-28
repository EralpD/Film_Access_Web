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
import com.example.film.service.FilmMaintenanceQueue;
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
    private FilmMaintenanceQueue maintenanceQueue;

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
                        maintenanceQueue,
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

        verify(maintenanceQueue).request(film, false);
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


        verify(maintenanceQueue).request(film, false);
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
        verify(maintenanceQueue).request(film, false);
    }

    @Test
    void staleCachedFilmReturnsImmediatelyAndQueuesRefresh() {
        when(film.getFetchedAt()).thenReturn(OffsetDateTime.now().minusDays(31));
        when(filmRepository.findByImdbId("tt1375666")).thenReturn(Optional.of(film));
        assertSame(film, service.getOrFetchFilm("tt1375666"));
        verify(maintenanceQueue).request(film, true);
        org.mockito.Mockito.verifyNoInteractions(filmDetailService);
    }

    @Test
    void backgroundRefreshRejectsWrongFilmIdentity() {
        when(filmDetailService.getFilmDetail("tt1375666")).thenReturn(detail);
        when(detail.getImdbId()).thenReturn("tt9999999");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.refreshMetadata("tt1375666"));
        org.mockito.Mockito.verifyNoInteractions(maintenanceQueue);
    }

    @Test
    void incompleteProviderRefreshDoesNotEraseKnownCredits() {
        Film existing = Film.createFromOmdb("tt1375666", "Inception", "2010", (short) 2010, "movie");
        existing.setActors("Tom Hardy"); existing.setDirector("Christopher Nolan");
        var incomplete = new FilmDetail("tt1375666", "Inception", "2010", 2010, null, null,
                null, "N/A", null, "Dreams", null, null, "movie");
        new FilmCatalogMapper().updateFilm(existing, incomplete);
        org.assertj.core.api.Assertions.assertThat(existing.getActors()).isEqualTo("Tom Hardy");
        org.assertj.core.api.Assertions.assertThat(existing.getDirector()).isEqualTo("Christopher Nolan");
    }
}

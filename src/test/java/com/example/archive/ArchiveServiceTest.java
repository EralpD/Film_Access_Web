package com.example.archive;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.archive.service.ArchiveService;
import com.example.film.Film;
import com.example.film.service.FilmCatalogDiscoveryService;
import com.example.search.ArchiveSemanticSearchRepository;
import com.example.search.service.ArchiveQueryEmbeddingService;
import com.example.user.User;
import com.example.user.UserRepository;



@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {


    @Mock
    private UserRepository userRepository;


    @Mock
    private UserFilmRepository userFilmRepository;


    @Mock
    private FilmCatalogDiscoveryService
            filmCatalogDiscoveryService;


    @Mock
    private ArchiveQueryEmbeddingService filmEmbeddingService;


    @Mock
    private ArchiveSemanticSearchRepository archiveSemanticSearchRepository;


    private ArchiveService archiveService;


    @BeforeEach
    void setUp() {

        archiveService =
                new ArchiveService(
                        userRepository,
                        userFilmRepository,
                        filmCatalogDiscoveryService,
                        filmEmbeddingService,
                        archiveSemanticSearchRepository
                );
    }


    @Test
    void shouldCreateFilmAndUserFilmWhenFilmIsNew() {

        String email =
                "user@example.com";

        String imdbId =
                "tt1375666";


        User user =
                org.mockito.Mockito.mock(
                        User.class
                );


        Film newFilm =
                org.mockito.Mockito.mock(
                        Film.class
                );


        when(user.getId())
                .thenReturn(1L);


        when(newFilm.getId())
                .thenReturn(10L);


        when(
                userRepository.findByEmail(email)
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(filmCatalogDiscoveryService.getOrFetchFilm(imdbId))
                .thenReturn(newFilm);


        when(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                1L,
                                10L
                        )
        )
                .thenReturn(false);


        archiveService.addFilmToArchive(
                email,
                imdbId
        );


        verify(filmCatalogDiscoveryService)
                .getOrFetchFilm(imdbId);


        verify(userFilmRepository)
                .save(
                        any(UserFilm.class)
                );
    }


    @Test
    void shouldRejectFilmWhenAlreadyInUsersArchive() {

        String email =
                "user@example.com";

        String imdbId =
                "tt1375666";


        User user =
                org.mockito.Mockito.mock(
                        User.class
                );


        when(user.getId())
                .thenReturn(1L);


        when(
                userRepository.findByEmail(email)
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(
                userFilmRepository
                        .existsByUser_IdAndFilm_ImdbId(
                                1L,
                                imdbId
                        )
        )
                .thenReturn(true);


        assertThrows(
                FilmAlreadyInArchiveException.class,
                () ->
                        archiveService.addFilmToArchive(
                                email,
                                imdbId
                        )
        );


        verify(
                filmCatalogDiscoveryService,
                never()
        )
                .getOrFetchFilm(imdbId);


        verify(
                userFilmRepository,
                never()
        )
                .save(
                        any(UserFilm.class)
                );
    }


    @Test
    void shouldAllowDifferentUserToAddExistingFilm() {

        String email =
                "second@example.com";

        String imdbId =
                "tt1375666";


        User user =
                org.mockito.Mockito.mock(
                        User.class
                );


        Film existingFilm =
                org.mockito.Mockito.mock(
                        Film.class
                );


        when(user.getId())
                .thenReturn(2L);


        when(existingFilm.getId())
                .thenReturn(10L);


        when(
                userRepository.findByEmail(email)
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(filmCatalogDiscoveryService.getOrFetchFilm(imdbId))
                .thenReturn(existingFilm);


        when(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                2L,
                                10L
                        )
        )
                .thenReturn(false);


        archiveService.addFilmToArchive(
                email,
                imdbId
        );


        verify(filmCatalogDiscoveryService)
                .getOrFetchFilm(imdbId);


        verify(userFilmRepository)
                .save(
                        any(UserFilm.class)
                );
    }


    @Test
    void shouldUseStructuredSearchWhenQueryIsBlank() {

        User user =
                new User();


        user.setId(1L);
        user.setEmail(
                "user@example.com"
        );


        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        request.setYearFrom(2010);


        when(
                userRepository.findByEmail(
                        "user@example.com"
                )
        )
                .thenReturn(
                        Optional.of(user)
                );


        /*
         * ARTIK Sort DEĞİL Pageable KULLANILIYOR.
         */
        when(
                userFilmRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        )
                .thenReturn(
                        Page.empty()
                );


        archiveService.searchArchive(
                "user@example.com",
                request
        );


        verify(
                filmEmbeddingService,
                never()
        )
                .find(
                        anyString()
                );


        verify(
                archiveSemanticSearchRepository,
                never()
        )
                .search(
                        anyLong(),
                        any(float[].class),
                        any(ArchiveSearchRequest.class),
                        any(Pageable.class),
                        any(ArchiveSortOption.class)
                );
    }


    @Test
    void shouldReturnRankedHybridFilmsAndCoverage() {
        User user = new User();
        user.setId(1L);
        ArchiveSearchRequest request = new ArchiveSearchRequest();
        request.setQuery("space adventure");
        float[] vector = {1f, 0f};
        var film = new ArchiveFilmResponse(10L, "tt1234567", "Interstellar", "2014", "movie",
                "Adventure", null, java.time.OffsetDateTime.now());
        var expected = new com.example.archive.response.ArchiveSearchResult(
                new PageImpl<>(List.of(film)), 2, 1, false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(archiveSemanticSearchRepository.coverage(1L, request))
                .thenReturn(new ArchiveSemanticSearchRepository.Coverage(2, 1));
        when(filmEmbeddingService.find("space adventure")).thenReturn(Optional.of(vector));
        when(archiveSemanticSearchRepository.search(eq(1L), eq(vector), eq(request),
                any(Pageable.class), eq(ArchiveSortOption.RELEVANCE_DESC))).thenReturn(expected);

        assertEquals(expected, archiveService.searchArchiveWithStatus("user@example.com", request));
        verify(userFilmRepository, never()).findByIdInAndUser_Id(any(), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Tenet", "Tennet", "Nolan", "Tom Hardy"})
    void directMatchesNeverCallEmbeddingOrCountCoverage(String query) {
        User user = new User(); user.setId(1L);
        var request = new ArchiveSearchRequest(); request.setQuery(query);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        var expected = new com.example.archive.response.ArchiveSearchResult(Page.empty(), 0, 0, false);
        when(archiveSemanticSearchRepository.directSearch(eq(1L), eq(request), any(), any()))
                .thenReturn(Optional.of(expected));
        assertEquals(expected, archiveService.searchArchiveWithStatus("user@example.com", request));
        verifyNoInteractions(filmEmbeddingService);
        verify(archiveSemanticSearchRepository, never()).coverage(anyLong(), any());
    }

    @Test
    void explicitlyRequestingMeaningBypassesDirectShortcut() {
        User user = new User(); user.setId(1L);
        var request = new ArchiveSearchRequest(); request.setQuery("Tenet"); request.setSemantic(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(archiveSemanticSearchRepository.coverage(1, request)).thenReturn(new ArchiveSemanticSearchRepository.Coverage(1, 1));
        when(filmEmbeddingService.find("Tenet")).thenReturn(Optional.of(new float[]{1, 0}));
        when(archiveSemanticSearchRepository.search(eq(1L), any(), eq(request), any(), any()))
                .thenReturn(new com.example.archive.response.ArchiveSearchResult(Page.empty(), 1, 0, false));
        archiveService.searchArchiveWithStatus("user@example.com", request);
        verify(archiveSemanticSearchRepository, never()).directSearch(anyLong(), any(), any(), any());
        verify(filmEmbeddingService).find("Tenet");
    }

    @Test
    void shouldKeepTitleSearchWhenEmbeddingApiFails() {
        assertTitleFallback(1, true);
    }

    @Test
    void shouldSkipEmbeddingApiWhenNoCurrentVectorsExist() {
        assertTitleFallback(0, false);
        verify(filmEmbeddingService, never()).find(anyString());
    }

    private void assertTitleFallback(long indexed, boolean unavailable) {
        User user = new User();
        user.setId(1L);
        ArchiveSearchRequest request = new ArchiveSearchRequest();
        request.setQuery("Interstelar");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(archiveSemanticSearchRepository.coverage(1L, request))
                .thenReturn(new ArchiveSemanticSearchRepository.Coverage(1, indexed));
        if (indexed > 0) when(filmEmbeddingService.find("Interstelar")).thenReturn(Optional.empty());
        var film = new ArchiveFilmResponse(10L, "tt1234567", "Interstellar", "2014", "movie",
                "Adventure", null, java.time.OffsetDateTime.now());
        when(archiveSemanticSearchRepository.search(eq(1L), org.mockito.ArgumentMatchers.isNull(),
                eq(request), any(Pageable.class), eq(ArchiveSortOption.RELEVANCE_DESC)))
                .thenReturn(new com.example.archive.response.ArchiveSearchResult(
                        new PageImpl<>(List.of(film)), 1, 1 - indexed, false));

        var result = archiveService.searchArchiveWithStatus("user@example.com", request);
        assertEquals("Interstellar", result.page().getContent().getFirst().title());
        assertEquals(unavailable, result.semanticUnavailable());
    }

    @Test
    void shouldAvoidApiAndRankingWhenFiltersHaveNoCandidates() {
        User user = new User();
        user.setId(1L);
        ArchiveSearchRequest request = new ArchiveSearchRequest();
        request.setQuery("space");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(archiveSemanticSearchRepository.coverage(1L, request))
                .thenReturn(new ArchiveSemanticSearchRepository.Coverage(0, 0));
        assertTrue(archiveService.searchArchiveWithStatus("user@example.com", request).page().isEmpty());
        org.mockito.Mockito.verifyNoInteractions(filmEmbeddingService);
    }

    @Test
    void shouldRejectInvalidFiltersBeforeDatabaseOrApiWork() {
        ArchiveSearchRequest request = new ArchiveSearchRequest();
        request.setYearFrom(2020);
        request.setYearTo(2000);
        assertThrows(IllegalArgumentException.class,
                () -> archiveService.searchArchive("user@example.com", request));
        org.mockito.Mockito.verifyNoInteractions(userRepository, filmEmbeddingService);
    }

    @Test
    void shouldUseRequestedPaginationForStructuredSearch() {

        User user =
                mock(User.class);


        when(user.getId())
                .thenReturn(10L);


        when(
                userRepository.findByEmail(
                        "user@test.com"
                )
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(
                userFilmRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        )
                .thenReturn(
                        Page.empty()
                );


        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        request.setPage(2);
        request.setSize(50);
        request.setSort(
                "title,asc"
        );


        archiveService.searchArchive(
                "user@test.com",
                request
        );


        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );


        verify(userFilmRepository)
                .findAll(
                        any(Specification.class),
                        captor.capture()
                );


        Pageable pageable =
                captor.getValue();


        assertEquals(
                2,
                pageable.getPageNumber()
        );


        assertEquals(
                50,
                pageable.getPageSize()
        );


        assertEquals(
                Sort.Direction.ASC,
                pageable
                        .getSort()
                        .getOrderFor(
                                "film.title"
                        )
                        .getDirection()
        );
    }


    @Test
    void shouldLimitArchivePageSizeToOneHundred() {

        User user =
                mock(User.class);


        when(user.getId())
                .thenReturn(10L);


        when(
                userRepository.findByEmail(
                        "user@test.com"
                )
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(
                userFilmRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        )
                .thenReturn(
                        Page.empty()
                );


        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        request.setSize(
                1_000_000
        );


        archiveService.searchArchive(
                "user@test.com",
                request
        );


        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );


        verify(userFilmRepository)
                .findAll(
                        any(Specification.class),
                        captor.capture()
                );


        assertEquals(
                100,
                captor
                        .getValue()
                        .getPageSize()
        );
    }
}


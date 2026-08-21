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
import com.example.search.service.FilmEmbeddingService;
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
    private FilmEmbeddingService filmEmbeddingService;


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
                .createQueryEmbedding(
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
    void shouldUseSemanticSearchWhenQueryExists() {

        User user =
                new User();


        user.setId(1L);
        user.setEmail(
                "user@example.com"
        );


        ArchiveSearchRequest request =
                new ArchiveSearchRequest();


        request.setQuery(
                "uzayda geçen duygusal bilim kurgu"
        );


        /*
         * TEK embedding değişkeni kullanıyoruz.
         */
        float[] queryEmbedding =
                new float[] {
                        0.1f,
                        0.2f,
                        0.3f
                };


        when(
                userRepository.findByEmail(
                        "user@example.com"
                )
        )
                .thenReturn(
                        Optional.of(user)
                );


        when(
                filmEmbeddingService
                        .createQueryEmbedding(
                                request.getQuery()
                        )
        )
                .thenReturn(
                        queryEmbedding
                );


        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );


        Page<Long> rankedPage =
                new PageImpl<>(
                        List.of(
                                10L,
                                20L
                        ),
                        pageable,
                        2
                );


        when(
                archiveSemanticSearchRepository
                        .search(
                                eq(1L),
                                eq(queryEmbedding),
                                eq(request),
                                any(Pageable.class),
                                eq(
                                        ArchiveSortOption
                                                .RELEVANCE_DESC
                                )
                        )
        )
                .thenReturn(
                        rankedPage
                );


        /*
         * Semantic repository yalnızca ID döndürüyor.
         * İkinci sorguda hiçbir UserFilm dönmezse
         * response doğal olarak boş olacaktır.
         */
        when(
                userFilmRepository
                        .findByIdInAndUser_Id(
                                List.of(
                                        10L,
                                        20L
                                ),
                                1L
                        )
        )
                .thenReturn(
                        List.of()
                );


        Page<ArchiveFilmResponse> result =
                archiveService.searchArchive(
                        "user@example.com",
                        request
                );


        assertTrue(
                result.isEmpty()
        );


        verify(
                filmEmbeddingService
        )
                .createQueryEmbedding(
                        request.getQuery()
                );


        verify(
                archiveSemanticSearchRepository
        )
                .search(
                        eq(user.getId()),
                        eq(queryEmbedding),
                        eq(request),
                        any(Pageable.class),
                        eq(
                                ArchiveSortOption
                                        .RELEVANCE_DESC
                        )
                );


        verify(
                userFilmRepository
        )
                .findByIdInAndUser_Id(
                        List.of(
                                10L,
                                20L
                        ),
                        user.getId()
                );
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


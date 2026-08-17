package com.example.archive;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.mapper.FilmCatalogMapper;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.archive.service.ArchiveService;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.user.User;
import com.example.user.UserRepository;
import com.example.search.service.FilmSemanticIndexService;
import com.example.search.service.FilmEmbeddingService;
import com.example.search.ArchiveSemanticSearchRepository;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilmRepository filmRepository;

    @Mock
    private UserFilmRepository userFilmRepository;

    @Mock
    private OmdbFilmDetailService filmDetailService;

    @Mock
    private FilmCatalogMapper filmCatalogMapper;

    @Mock
    private FilmSemanticIndexService filmSemanticIndexService;

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
                        filmRepository,
                        userFilmRepository,
                        filmDetailService,
                        filmCatalogMapper,
                        filmSemanticIndexService,
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

        FilmDetail detail =
                org.mockito.Mockito.mock(
                        FilmDetail.class
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

        when(
            filmDetailService
                    .getFilmDetail(imdbId)
        )
                .thenReturn(detail);

        when(
            filmRepository
                    .findByImdbId(imdbId)
        )
                .thenReturn(
                        Optional.empty()
                );

        when(
            filmCatalogMapper
                    .toFilm(detail)
        )
                .thenReturn(newFilm);

        when(
            filmRepository.save(newFilm)
        )
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

        verify(filmRepository)
                .save(newFilm);

        verify(userFilmRepository)
                .save(any(UserFilm.class));
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

        FilmDetail detail =
                org.mockito.Mockito.mock(
                        FilmDetail.class
                );

        Film existingFilm =
                org.mockito.Mockito.mock(
                        Film.class
                );

        when(user.getId())
                .thenReturn(1L);

        when(existingFilm.getId())
                .thenReturn(10L);

        when(
                userRepository.findByEmail(email)
        )
                .thenReturn(
                        Optional.of(user)
                );

        when(
                filmDetailService
                        .getFilmDetail(imdbId)
        )
                .thenReturn(detail);

        when(
                filmRepository
                        .findByImdbId(imdbId)
        )
                .thenReturn(
                        Optional.of(existingFilm)
                );

        when(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                1L,
                                10L
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

        verify(filmRepository, never())
                .save(any(Film.class));

        verify(userFilmRepository, never())
                .save(any(UserFilm.class));
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

        FilmDetail detail =
                org.mockito.Mockito.mock(
                        FilmDetail.class
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

        when(
                filmDetailService
                        .getFilmDetail(imdbId)
        )
                .thenReturn(detail);

        when(
                filmRepository
                        .findByImdbId(imdbId)
        )
                .thenReturn(
                        Optional.of(existingFilm)
                );

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

        verify(filmRepository, never())
                .save(any(Film.class));

        verify(userFilmRepository)
                .save(any(UserFilm.class));
        }

        @Test
        void shouldUseStructuredSearchWhenQueryIsBlank() {

        User user = new User();

        user.setId(1L);
        user.setEmail("user@example.com");


        ArchiveSearchRequest request =
                new ArchiveSearchRequest();

        request.setYearFrom(2010);


        when(
                userRepository.findByEmail(
                "user@example.com"
                )
        ).thenReturn(
                Optional.of(user)
        );


        when(
                userFilmRepository.findAll(
                any(Specification.class),
                any(Sort.class)
                )
        ).thenReturn(
                List.of()
        );


        archiveService.searchArchive(
                "user@example.com",
                request
        );


        verify(
                filmEmbeddingService,
                never()
        ).createQueryEmbedding(
                anyString()
        );


        verify(
                archiveSemanticSearchRepository,
                never()
        ).search(
                anyLong(),
                any(),
                any()
        );
        }
@Test
void shouldUseSemanticSearchWhenQueryExists() {

    User user = new User();

    user.setId(1L);
    user.setEmail("user@example.com");


    ArchiveSearchRequest request =
            new ArchiveSearchRequest();

    request.setQuery(
            "uzayda geçen duygusal bilim kurgu"
    );


    float[] embedding =
            new float[] {
                    0.1f,
                    0.2f,
                    0.3f
            };


    when(
        userRepository.findByEmail(
            "user@example.com"
        )
    ).thenReturn(
        Optional.of(user)
    );


    when(
        filmEmbeddingService
                .createQueryEmbedding(
                        request.getQuery()
                )
    ).thenReturn(
        embedding
    );


    when(
        archiveSemanticSearchRepository
                .search(
                        1L,
                        embedding,
                        request
                )
    ).thenReturn(
        List.of()
    );


    List<ArchiveFilmResponse> result =
            archiveService.searchArchive(
                    "user@example.com",
                    request
            );


    assertTrue(result.isEmpty());


    verify(
        filmEmbeddingService
    ).createQueryEmbedding(
        request.getQuery()
    );


    verify(
        archiveSemanticSearchRepository
    ).search(
        1L,
        embedding,
        request
    );
}

}

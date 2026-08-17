package com.example.archive.service;

import java.time.OffsetDateTime;
import java.util.List;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.archive.ArchiveSearchRequest;
import com.example.archive.UserFilm;
import com.example.archive.UserFilmRepository;
import com.example.archive.UserFilmSpecifications;
import com.example.archive.exception.ArchiveEntryNotFoundException;
import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.mapper.FilmCatalogMapper;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.search.service.FilmSemanticIndexService;
import com.example.user.User;
import com.example.user.UserRepository;
import com.example.search.service.FilmEmbeddingService;
import com.example.search.ArchiveSemanticSearchRepository;

@Service
public class ArchiveService {

    private final UserRepository userRepository;
    private final FilmRepository filmRepository;
    private final UserFilmRepository userFilmRepository;
    private final OmdbFilmDetailService filmDetailService;
    private final FilmCatalogMapper filmCatalogMapper;
    private final FilmSemanticIndexService filmSemanticIndexService;
    private final FilmEmbeddingService filmEmbeddingService;
    private final ArchiveSemanticSearchRepository archiveSemanticSearchRepository;

    public ArchiveService(
            UserRepository userRepository,
            FilmRepository filmRepository,
            UserFilmRepository userFilmRepository,
            OmdbFilmDetailService filmDetailService,
            FilmCatalogMapper filmCatalogMapper,
            FilmSemanticIndexService filmSemanticIndexService,
            FilmEmbeddingService filmEmbeddingService,
            ArchiveSemanticSearchRepository archiveSemanticSearchRepository
    ) {
        this.userRepository = userRepository;
        this.filmRepository = filmRepository;
        this.userFilmRepository =
                userFilmRepository;
        this.filmDetailService =
                filmDetailService;
        this.filmCatalogMapper =
                filmCatalogMapper;
        this.filmSemanticIndexService = filmSemanticIndexService;
        this.filmEmbeddingService = filmEmbeddingService;
        this.archiveSemanticSearchRepository = archiveSemanticSearchRepository;
    }

    @Transactional
    public UserFilm addFilmToArchive(
            String userEmail,
            String imdbId
    ) {

        User user = userRepository
                .findByEmail(
                        normalizeEmail(userEmail)
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user was not found."
                        )
                );

        FilmDetail detail =
                filmDetailService
                        .getFilmDetail(imdbId);

        Film film = filmRepository
                .findByImdbId(detail.getImdbId())
                .orElseGet(() -> {

                Film newFilm =
                        filmCatalogMapper.toFilm(detail);

                return filmRepository.save(newFilm);
                });


        filmSemanticIndexService.indexFilm(film);


        if (userFilmRepository.existsByUserIdAndFilmId(
                user.getId(),
                film.getId()
        )) {

                throw new FilmAlreadyInArchiveException(
                                imdbId
                        );
        }


        UserFilm userFilm =
                new UserFilm(
                        user,
                        film,
                        OffsetDateTime.now()
                );

        userFilmRepository.save(userFilm);


        boolean alreadyExists =
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                user.getId(),
                                film.getId()
                        );

        if (alreadyExists) {
            throw new FilmAlreadyInArchiveException(
                    imdbId
            );
        }

        return userFilmRepository
                .save(userFilm);
    }

    private String normalizeEmail(
            String email
    ) {

        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "User email cannot be empty."
            );
        }

        return email
                .trim()
                .toLowerCase();
    }

    @Transactional(readOnly = true)
    public List<ArchiveFilmResponse> getArchive(String email){
        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> 
                                new IllegalStateException("Authenticated user couldn't be found")
                                );

        return userFilmRepository
                        .findAllByUser_IdOrderByAddedAtDesc(user.getId())
                        .stream()
                        .map(ArchiveFilmResponse::from)
                        .toList();
    }

    @Transactional
    public void deleteArchive(String email, Long userFilmId){
        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> 
                                new IllegalStateException("Authenticated user couldn't be found")
                                );

        UserFilm userFilm = userFilmRepository
                                .findByIdAndUser_Id(userFilmId, user.getId())
                                .orElseThrow(ArchiveEntryNotFoundException::new);

        userFilmRepository.delete(userFilm);
    }

    @Transactional(readOnly = true)
        public List<ArchiveFilmResponse> searchArchive(
                String email,
                ArchiveSearchRequest request
        ) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user could not be found."
                        )
                );


        if (request.hasSemanticQuery()) {

                return semanticSearch(
                        user.getId(),
                        request
                );
        }


        return structuredSearch(
                user.getId(),
                request
        );
        }

        private List<ArchiveFilmResponse> structuredSearch(
                Long userId,
                ArchiveSearchRequest request
        ) {

        Specification<UserFilm> specification =
                UserFilmSpecifications
                        .belongsToUser(userId);


        if (request.getYearFrom() != null) {

                specification =
                        specification.and(
                                UserFilmSpecifications
                                        .releaseYearAtLeast(
                                                request.getYearFrom()
                                        )
                        );
        }


        if (request.getYearTo() != null) {

                specification =
                        specification.and(
                                UserFilmSpecifications
                                        .releaseYearAtMost(
                                                request.getYearTo()
                                        )
                        );
        }


        if (request.getType() != null) {

                specification =
                        specification.and(
                                UserFilmSpecifications
                                        .hasType(
                                                request.getType()
                                        )
                        );
        }


        if (request.hasGenre()) {

                specification =
                        specification.and(
                                UserFilmSpecifications
                                        .hasGenre(
                                                request.getGenre()
                                        )
                        );
        }


        List<UserFilm> userFilms =
                userFilmRepository.findAll(
                        specification,
                        Sort.by(
                                Sort.Direction.DESC,
                                "addedAt"
                        )
                );


        return userFilms
                        .stream()
                        .map(ArchiveFilmResponse::from)
                        .toList();
                }

                private List<ArchiveFilmResponse> semanticSearch(
                Long userId,
                ArchiveSearchRequest request
        ) {

        float[] queryEmbedding =
                filmEmbeddingService
                        .createQueryEmbedding(
                                request.getQuery()
                        );


        List<Long> rankedIds =
                archiveSemanticSearchRepository
                        .search(
                                userId,
                                queryEmbedding,
                                request
                        );


        if (rankedIds.isEmpty()) {
                return List.of();
        }


        List<UserFilm> userFilms =
                userFilmRepository
                        .findByIdIn(rankedIds);


        Map<Long, UserFilm> filmsById =
                new HashMap<>();


        for (UserFilm userFilm : userFilms) {

                filmsById.put(
                        userFilm.getId(),
                        userFilm
                );
        }


        List<ArchiveFilmResponse> result =
                new ArrayList<>();


        for (Long id : rankedIds) {

                UserFilm userFilm =
                        filmsById.get(id);

                if (userFilm != null) {

                result.add(
                        ArchiveFilmResponse.from(
                                userFilm
                        )
                );
                }
        }


        return result;
        }




}

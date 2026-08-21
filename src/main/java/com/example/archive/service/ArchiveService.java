package com.example.archive.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.archive.option.ArchiveSortOption;

import com.example.archive.ArchiveSearchRequest;
import com.example.archive.UserFilm;
import com.example.archive.UserFilmRepository;
import com.example.archive.UserFilmSpecifications;
import com.example.archive.exception.ArchiveEntryNotFoundException;
import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.film.Film;
import com.example.film.service.FilmCatalogDiscoveryService;
import com.example.search.ArchiveSemanticSearchRepository;
import com.example.search.service.FilmEmbeddingService;
import com.example.user.User;
import com.example.user.UserRepository;

@Service
public class ArchiveService {

    private final UserRepository userRepository;
    private final UserFilmRepository userFilmRepository;
    private final FilmCatalogDiscoveryService
            filmCatalogDiscoveryService;
    private final FilmEmbeddingService filmEmbeddingService;
    private final ArchiveSemanticSearchRepository archiveSemanticSearchRepository;

    public ArchiveService(
            UserRepository userRepository,
            UserFilmRepository userFilmRepository,
            FilmCatalogDiscoveryService filmCatalogDiscoveryService,
            FilmEmbeddingService filmEmbeddingService,
            ArchiveSemanticSearchRepository archiveSemanticSearchRepository
    ) {
        this.userRepository = userRepository;
        this.userFilmRepository =
                userFilmRepository;
        this.filmCatalogDiscoveryService =
                filmCatalogDiscoveryService;
        this.filmEmbeddingService = filmEmbeddingService;
        this.archiveSemanticSearchRepository = archiveSemanticSearchRepository;
    }

@Transactional
public UserFilm addFilmToArchive(
        String userEmail,
        String imdbId
) {

    User user =
            userRepository
                    .findByEmail(
                            normalizeEmail(userEmail)
                    )
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Authenticated user was not found."
                            )
                    );


    /*
     * Film zaten arşivdeyse OMDb veya OpenAI çağrısı yapmadan çık.
     */
    if (userFilmRepository.existsByUser_IdAndFilm_ImdbId(
            user.getId(),
            imdbId
    )) {
        throw new FilmAlreadyInArchiveException(imdbId);
    }


    Film film =
            filmCatalogDiscoveryService
                    .getOrFetchFilm(imdbId);


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


    return userFilmRepository.save(
            userFilm
    );
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
        public Page<ArchiveFilmResponse> searchArchive(
                String email,
                ArchiveSearchRequest request
        ) {

        User user =
                userRepository
                        .findByEmail(
                                normalizeEmail(email)
                        )
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


  private Page<ArchiveFilmResponse> structuredSearch(
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


        ArchiveSortOption sortOption =
                request.resolveSortOption();


        Pageable pageable =
                PageRequest.of(
                        request.normalizedPage(),
                        request.normalizedSize(),
                        sortOption.toJpaSort()
                );


        Page<UserFilm> userFilms =
                userFilmRepository.findAll(
                        specification,
                        pageable
                );


        return userFilms.map(
                ArchiveFilmResponse::from
        );
        }



private Page<ArchiveFilmResponse> semanticSearch(
                Long userId,
                ArchiveSearchRequest request
        ) {

        float[] queryEmbedding =
                filmEmbeddingService
                        .createQueryEmbedding(
                                request.getQuery().trim()
                        );


        ArchiveSortOption sortOption =
                request.resolveSortOption();


        Pageable pageable =
                PageRequest.of(
                        request.normalizedPage(),
                        request.normalizedSize()
                );


        Page<Long> rankedPage =
                archiveSemanticSearchRepository
                        .search(
                                userId,
                                queryEmbedding,
                                request,
                                pageable,
                                sortOption
                        );


        List<Long> rankedIds =
                rankedPage.getContent();


        if (rankedIds.isEmpty()) {

                return new PageImpl<>(
                        List.of(),
                        pageable,
                        rankedPage.getTotalElements()
                );
        }


        /*
        * İkinci ownership kontrolü.
        */
        List<UserFilm> userFilms =
                userFilmRepository
                        .findByIdInAndUser_Id(
                                rankedIds,
                                userId
                        );


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


        /*
        * findByIdIn SQL sırasını garanti etmez.
        * pgvector sırasını burada tekrar kuruyoruz.
        */
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


        return new PageImpl<>(
                result,
                pageable,
                rankedPage.getTotalElements()
        );
        }





}

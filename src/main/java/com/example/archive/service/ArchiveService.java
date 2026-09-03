package com.example.archive.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
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
import com.example.search.service.ArchiveQueryEmbeddingService;
import com.example.archive.response.ArchiveSearchResult;
import com.example.user.User;
import com.example.user.UserRepository;

@Service
public class ArchiveService {

    private final UserRepository userRepository;
    private final UserFilmRepository userFilmRepository;
    private final FilmCatalogDiscoveryService
            filmCatalogDiscoveryService;
    private final ArchiveQueryEmbeddingService filmEmbeddingService;
    private final ArchiveSemanticSearchRepository archiveSemanticSearchRepository;
    private final int catalogMinProfileFilms;

    public ArchiveService(
            UserRepository userRepository,
            UserFilmRepository userFilmRepository,
            FilmCatalogDiscoveryService filmCatalogDiscoveryService,
            ArchiveQueryEmbeddingService filmEmbeddingService,
            ArchiveSemanticSearchRepository archiveSemanticSearchRepository,
            @Value("${catalog.personalization.min-profile-films:3}") int catalogMinProfileFilms
    ) {
        this.userRepository = userRepository;
        this.userFilmRepository =
                userFilmRepository;
        this.filmCatalogDiscoveryService =
                filmCatalogDiscoveryService;
        this.filmEmbeddingService = filmEmbeddingService;
        this.archiveSemanticSearchRepository = archiveSemanticSearchRepository;
        this.catalogMinProfileFilms = Math.max(1, catalogMinProfileFilms);
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
                .toLowerCase(java.util.Locale.ROOT);
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

        public Page<ArchiveFilmResponse> searchArchive(String email, ArchiveSearchRequest request) {
        return searchArchiveWithStatus(email, request).page();
    }

    public ArchiveSearchResult searchArchiveWithStatus(String email, ArchiveSearchRequest request) {
        validateSearch(request);
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be found."));
        if (!request.hasSemanticQuery() && !request.hasPersonFilter()) {
            Page<ArchiveFilmResponse> page = structuredSearch(user.getId(), request);
            return new ArchiveSearchResult(page, page.getTotalElements(), 0, false);
        }
        return searchInScope(user.getId(), request);
    }

    public ArchiveSearchResult searchCatalogWithStatus(ArchiveSearchRequest request) {
        validateSearch(request);
        return searchInScope(null, request);
    }

    @Transactional(readOnly = true)
    public ArchiveSearchResult browseCatalogForUser(String email, ArchiveSearchRequest request) {
        validateSearch(request);
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be found."));

        if (request.getSort() != null
                && !request.getSort().isBlank()
                && !"auto".equalsIgnoreCase(request.getSort().trim())) {
            return searchInScope(null, request);
        }

        Pageable pageable = PageRequest.of(request.normalizedPage(), request.normalizedSize());
        return archiveSemanticSearchRepository.browseCatalog(
                user.getId(), pageable, catalogMinProfileFilms);
    }

    private void validateSearch(ArchiveSearchRequest request) {
        if (!request.isYearRangeValid()
                || (request.getYearFrom() != null && (request.getYearFrom() < 1888 || request.getYearFrom() > 2100))
                || (request.getYearTo() != null && (request.getYearTo() < 1888 || request.getYearTo() > 2100))
                || (request.getQuery() != null && request.getQuery().length() > 500)
                || (request.getGenre() != null && request.getGenre().length() > 100)
                || (request.getActor() != null && request.getActor().length() > 200)
                || (request.getDirector() != null && request.getDirector().length() > 200)) {
            throw new IllegalArgumentException("Invalid archive search filters.");
        }
    }

    private ArchiveSearchResult searchInScope(Long userId, ArchiveSearchRequest request) {
        long started = System.nanoTime();
        Pageable pageable = PageRequest.of(request.normalizedPage(), request.normalizedSize());
        if (!request.isSemantic() || !request.hasSemanticQuery()) {
            var direct = userId == null
                    ? archiveSemanticSearchRepository.directCatalogSearch(request, pageable, request.resolveSortOption())
                    : archiveSemanticSearchRepository.directSearch(userId, request, pageable, request.resolveSortOption());
            if (direct.isPresent()) return logSearch(direct.get(), started, userId == null);
        }
        var coverage = userId == null ? archiveSemanticSearchRepository.catalogCoverage(request)
                : archiveSemanticSearchRepository.coverage(userId, request);
        if (coverage.total() == 0)
            return new ArchiveSearchResult(new PageImpl<>(List.of(), pageable, 0), 0, 0, false);
        float[] embedding = coverage.indexed() == 0 ? null
                : filmEmbeddingService.find(request.getQuery()).orElse(null);
        var result = userId == null
                ? archiveSemanticSearchRepository.searchCatalog(embedding, request, pageable, request.resolveSortOption())
                : archiveSemanticSearchRepository.search(userId, embedding, request, pageable, request.resolveSortOption());
        return logSearch(result.withSemanticUnavailable(coverage.indexed() > 0 && embedding == null), started, userId == null);
    }

    private ArchiveSearchResult logSearch(ArchiveSearchResult result, long started, boolean catalog) {
        // No email, query, person names, or provider response bodies in performance logs.
        org.slf4j.LoggerFactory.getLogger(ArchiveService.class).debug(
                "Film search scope={} mode={} durationMs={} matches={} spellingLimited={}",
                catalog ? "catalog" : "archive", result.mode(), (System.nanoTime() - started) / 1_000_000,
                result.page().getTotalElements(), result.spellingCandidatesLimited());
        return result;
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



}

package com.example.discovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.archive.response.ArchiveSearchResult;
import com.example.archive.service.ArchiveService;
import com.example.film.FilmRepository;
import com.example.omdb.dto.OmdbSearchItem;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.service.OmdbService;

@Service
public class DiscoverySearchService {

    private final ArchiveService archiveService;
    private final OmdbService omdbService;
    private final FilmRepository filmRepository;

    public DiscoverySearchService(
            ArchiveService archiveService,
            OmdbService omdbService,
            FilmRepository filmRepository
    ) {
        this.archiveService = archiveService;
        this.omdbService = omdbService;
        this.filmRepository = filmRepository;
    }

    public DiscoverySearchResult search(DiscoverySearchRequest request) {
        SearchScope scope = request.resolvedScope();
        if (!request.hasSearchCriteria()) return DiscoverySearchResult.empty(scope);

        ArchiveSearchResult catalog = null;
        String catalogError = null;
        if (scope.includesCatalog()) {
            try {
                catalog = archiveService.searchCatalogWithStatus(request.toCatalogRequest());
            } catch (RuntimeException failure) {
                catalogError = "The local catalog is temporarily unavailable.";
            }
        }

        List<OmdbSearchItem> rawOmdb = List.of();
        int omdbTotalPages = 0;
        String omdbError = null;
        if (scope.includesOmdb() && request.hasQuery()) {
            try {
                OmdbSearchResponse response = omdbService.searchMovies(
                        request.normalizedQuery(), request.getYear(), request.resolvedType(),
                        request.normalizedOmdbPage());
                if (response == null) {
                    omdbError = "The OMDb service did not return a response.";
                } else if (!response.isSuccessful()) {
                    omdbError = response.getError() == null || response.getError().isBlank()
                            ? "No OMDb titles matched this search."
                            : response.getError();
                } else {
                    rawOmdb = response.getSearch() == null ? List.of() : response.getSearch();
                    omdbTotalPages = totalPages(response.getTotalResults());
                }
            } catch (RuntimeException failure) {
                omdbError = "OMDb is temporarily unavailable.";
            }
        }

        LinkedHashMap<String, OmdbSearchItem> uniqueOmdb = new LinkedHashMap<>();
        for (OmdbSearchItem film : rawOmdb) {
            if (film != null && film.getImdbId() != null && !film.getImdbId().isBlank()) {
                uniqueOmdb.putIfAbsent(film.getImdbId(), film);
            }
        }

        Set<String> existingIds = uniqueOmdb.isEmpty()
                ? Set.of()
                : filmRepository.findExistingImdbIds(uniqueOmdb.keySet());

        List<DiscoveryOmdbFilm> omdbFilms = uniqueOmdb.values().stream()
                .map(film -> new DiscoveryOmdbFilm(
                        film.getImdbId(), film.getTitle(), film.getYear(), film.getType(), film.getPoster(),
                        existingIds.contains(film.getImdbId())))
                .toList();

        return new DiscoverySearchResult(
                scope, true, catalog, omdbFilms, request.normalizedOmdbPage(), omdbTotalPages,
                catalogError, omdbError);
    }

    public DiscoverySearchResult browseCatalog(String userEmail, DiscoverySearchRequest request) {
        ArchiveSearchResult catalog = null;
        String catalogError = null;
        try {
            catalog = archiveService.browseCatalogForUser(userEmail, request.toCatalogRequest());
        } catch (RuntimeException failure) {
            catalogError = "The local catalog is temporarily unavailable.";
        }

        return new DiscoverySearchResult(
                SearchScope.CATALOG, true, catalog, List.of(), 1, 0,
                catalogError, null);
    }

    private int totalPages(String totalResults) {
        if (totalResults == null || totalResults.isBlank()) return 0;
        try { return (int) Math.ceil(Integer.parseInt(totalResults) / 10.0); }
        catch (NumberFormatException ignored) { return 0; }
    }
}

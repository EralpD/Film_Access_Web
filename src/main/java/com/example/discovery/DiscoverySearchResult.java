package com.example.discovery;

import java.util.List;

import com.example.archive.response.ArchiveSearchResult;

public record DiscoverySearchResult(
        SearchScope scope,
        boolean searched,
        ArchiveSearchResult catalog,
        List<DiscoveryOmdbFilm> omdbFilms,
        int omdbPage,
        int omdbTotalPages,
        String catalogError,
        String omdbError
) {
    public static DiscoverySearchResult empty(SearchScope scope) {
        return new DiscoverySearchResult(scope, false, null, List.of(), 1, 0, null, null);
    }

    public boolean catalogAttempted() { return searched && scope.includesCatalog(); }
    public boolean omdbAttempted() { return searched && scope.includesOmdb(); }
    public boolean hasCatalogFilms() { return catalog != null && !catalog.page().isEmpty(); }
    public boolean hasOmdbFilms() { return omdbFilms != null && !omdbFilms.isEmpty(); }
    public boolean hasAnyFilms() { return hasCatalogFilms() || hasOmdbFilms(); }
    public boolean partialFailure() {
        return (catalogError != null && hasOmdbFilms()) || (omdbError != null && hasCatalogFilms());
    }
}

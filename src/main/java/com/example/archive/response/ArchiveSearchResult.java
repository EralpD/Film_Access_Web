package com.example.archive.response;

import org.springframework.data.domain.Page;

/** Coverage is separate from matches: no matches does not imply a healthy index. */
public record ArchiveSearchResult(Page<ArchiveFilmResponse> page, long eligibleFilms,
        long unindexedFilms, boolean semanticUnavailable, boolean spellingCandidatesLimited, String mode) {
    public ArchiveSearchResult(Page<ArchiveFilmResponse> page, long eligibleFilms,
            long unindexedFilms, boolean semanticUnavailable) {
        this(page, eligibleFilms, unindexedFilms, semanticUnavailable, false, "hybrid");
    }
    public ArchiveSearchResult withSemanticUnavailable(boolean unavailable) {
        return new ArchiveSearchResult(page, eligibleFilms, unindexedFilms, unavailable, spellingCandidatesLimited, mode);
    }
}

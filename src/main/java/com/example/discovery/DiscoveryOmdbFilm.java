package com.example.discovery;

public record DiscoveryOmdbFilm(
        String imdbId,
        String title,
        String year,
        String type,
        String posterUrl,
        boolean inCatalog
) {
}

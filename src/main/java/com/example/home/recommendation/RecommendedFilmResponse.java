package com.example.home.recommendation;

public record RecommendedFilmResponse(

        String imdbId,

        String title,

        String yearText,

        String type,

        String genresText,

        String posterUrl

) {

    static RecommendedFilmResponse from(
            RecommendationCandidate candidate
    ) {

        return new RecommendedFilmResponse(
                candidate.imdbId(),
                candidate.title(),
                candidate.yearText(),
                candidate.type(),
                candidate.genresText(),
                candidate.posterUrl()
        );
    }
}
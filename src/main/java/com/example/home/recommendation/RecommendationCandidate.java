package com.example.home.recommendation;

record RecommendationCandidate(

        Long filmId,

        String imdbId,

        String title,

        String yearText,

        String type,

        String genresText,

        String posterUrl,

        double recommendationScore

) {
}
package com.example.buddy.repository;

public record BuddyFilmCandidate(

        String imdbId,
        String title,
        String yearText,
        String type,
        String genresText,
        String posterUrl,
        double totalScore

) {
}
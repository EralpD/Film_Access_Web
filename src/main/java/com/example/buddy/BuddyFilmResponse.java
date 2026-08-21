package com.example.buddy;

public record BuddyFilmResponse(

        String imdbId,
        String title,
        String yearText,
        String type,
        String genresText,
        String posterUrl,
        String matchLabel

) {
}
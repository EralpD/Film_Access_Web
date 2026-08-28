package com.example.archive.response;

import java.time.OffsetDateTime;

import com.example.archive.UserFilm;
import com.example.film.Film;

public record ArchiveFilmResponse(

        Long userFilmId,
        String imdbId,
        String title,
        String yearText,
        String type,
        String genresText,
        String posterUrl,
        OffsetDateTime addedAt,
        String matchReason

) {
    public ArchiveFilmResponse(Long userFilmId, String imdbId, String title, String yearText,
            String type, String genresText, String posterUrl, OffsetDateTime addedAt) {
        this(userFilmId, imdbId, title, yearText, type, genresText, posterUrl, addedAt, null);
    }

    public static ArchiveFilmResponse from(UserFilm userFilm) {

        Film film = userFilm.getFilm();

        return new ArchiveFilmResponse(
                userFilm.getId(),
                film.getImdbId(),
                film.getTitle(),
                film.getYearText(),
                film.getType() == null
                        ? null
                        : film.getType().toString(),
                film.getGenresText(),
                film.getPosterUrl(),
                userFilm.getAddedAt()
        );
    }
}

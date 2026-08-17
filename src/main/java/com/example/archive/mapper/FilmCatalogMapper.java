package com.example.archive.mapper;

import org.springframework.stereotype.Component;

import com.example.film.Film;
import com.example.omdb.model.FilmDetail;

@Component
public class FilmCatalogMapper {

    public Film toFilm(FilmDetail detail) {

        String yearText =
                normalize(detail.getYearText());

        Short releaseYear =
                extractReleaseYear(yearText);

        String type =
                normalize(detail.getType());

        return Film.createFromOmdb(
                detail.getImdbId(),
                detail.getTitle(),
                yearText,
                releaseYear,
                type
        );
    }

    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isEmpty()
                || "N/A".equalsIgnoreCase(normalized)) {
            return null;
        }

        return normalized;
    }

    private Short extractReleaseYear(String yearText) {

        if (yearText == null
                || yearText.length() < 4) {
            return null;
        }

        String firstFour =
                yearText.substring(0, 4);

        try {
            return Short.valueOf(firstFour);

        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

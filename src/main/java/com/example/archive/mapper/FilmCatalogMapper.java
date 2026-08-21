package com.example.archive.mapper;

import java.util.Locale;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.example.film.Film;
import com.example.omdb.model.FilmDetail;
import java.util.Objects;

@Component
public class FilmCatalogMapper {

    public Film toFilm(
            FilmDetail detail
    ) {

        Film film =
                Film.createFromOmdb(
                        detail.getImdbId(),
                        detail.getTitle(),
                        normalize(detail.getYearText()),
                        toShort(detail.getReleaseYear()),
                        normalizeType(detail.getType())
                );

        updateFilm(
                film,
                detail
        );

        return film;
    }


    public FilmDetail toFilmDetail(
            Film film
    ) {

        Objects.requireNonNull(
                film,
                "Film cannot be null"
        );

        return new FilmDetail(
                film.getImdbId(),
                film.getTitle(),
                film.getYearText(),
                film.getReleaseYear() == null
                        ? null
                        : film.getReleaseYear().intValue(),
                film.getReleasedOn(),
                film.getRuntimeMinutes() == null
                        ? null
                        : film.getRuntimeMinutes().intValue(),
                film.getGenresText(),
                film.getDirector(),
                film.getActors(),
                film.getPlot(),
                film.getPosterUrl(),
                film.getImdbRating(),
                film.getType()
        );
    }


   public boolean updateFilm(
        Film film,
        FilmDetail detail
) {

    String normalizedYearText =
            normalize(
                    detail.getYearText()
            );

    Short normalizedReleaseYear =
            toShort(
                    detail.getReleaseYear()
            );

    String normalizedType =
            normalizeType(
                    detail.getType()
            );

    String normalizedGenre =
            normalize(
                    detail.getGenre()
            );

    String normalizedDirector =
            normalize(
                    detail.getDirector()
            );

    String normalizedActors =
            normalize(
                    detail.getActors()
            );

    String normalizedPlot =
            normalize(
                    detail.getPlot()
            );

    String normalizedPosterUrl =
            normalize(
                    detail.getPosterUrl()
            );

    boolean semanticContentChanged =
            !Objects.equals(
                    film.getTitle(),
                    detail.getTitle()
            )
            || !Objects.equals(
                    film.getYearText(),
                    normalizedYearText
            )
            || !Objects.equals(
                    film.getType(),
                    normalizedType
            )
            || !Objects.equals(
                    film.getGenresText(),
                    normalizedGenre
            )
            || !Objects.equals(
                    film.getDirector(),
                    normalizedDirector
            )
            || !Objects.equals(
                    film.getActors(),
                    normalizedActors
            )
            || !Objects.equals(
                    film.getPlot(),
                    normalizedPlot
            );


    film.setImdbId(
            detail.getImdbId()
    );

    film.setTitle(
            detail.getTitle()
    );

    film.setYearText(
            normalizedYearText
    );

    film.setReleaseYear(
            normalizedReleaseYear
    );

    film.setType(
            normalizedType
    );

    film.setReleasedOn(
            detail.getReleasedOn()
    );

    film.setRuntimeMinutes(
            toShort(
                    detail.getRuntimeMinutes()
            )
    );

    film.setGenresText(
            normalizedGenre
    );

    film.setDirector(
            normalizedDirector
    );

    film.setActors(
            normalizedActors
    );

    film.setPlot(
            normalizedPlot
    );

    film.setPosterUrl(
            normalizedPosterUrl
    );

    film.setImdbRating(
            detail.getImdbRating()
    );

    OffsetDateTime now = OffsetDateTime.now();

    film.setFetchedAt(now);
    film.setUpdatedAt(now);


    return semanticContentChanged;
}


    private String normalize(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isBlank()
                || "N/A".equalsIgnoreCase(normalized)) {

            return null;
        }

        return normalized;
    }


    private String normalizeType(
            String value
    ) {

        String normalized =
                normalize(value);

        if (normalized == null) {
            return null;
        }

        return normalized.toUpperCase(
                Locale.ROOT
        );
    }


    private Short toShort(
            Integer value
    ) {

        if (value == null
                || value < Short.MIN_VALUE
                || value > Short.MAX_VALUE) {

            return null;
        }

        return value.shortValue();
    }
}

package com.example.archive;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.example.omdb.dto.OmdbType;

public final class UserFilmSpecifications {

    private UserFilmSpecifications() {
    }


    public static Specification<UserFilm> belongsToUser(
            Long userId
    ) {

        return (root, query, cb) ->
                cb.equal(
                        root.get("user").get("id"),
                        userId
                );
    }


    public static Specification<UserFilm> releaseYearAtLeast(
            Integer year
    ) {

        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(
                        root
                                .get("film")
                                .get("releaseYear")
                                .as(Integer.class),
                        year
                );
    }


    public static Specification<UserFilm> releaseYearAtMost(
            Integer year
    ) {

        return (root, query, cb) ->
                cb.lessThanOrEqualTo(
                        root
                                .get("film")
                                .get("releaseYear")
                                .as(Integer.class),
                        year
                );
    }


public static Specification<UserFilm> hasType(
        OmdbType type
        ) {

        return (root, query, cb) ->
                cb.equal(
                        cb.upper(
                                root
                                        .get("film")
                                        .get("type")
                                        .as(String.class)
                        ),
                        type.name()
                );
        }


    public static Specification<UserFilm> hasGenre(
            String genre
    ) {

        String normalizedGenre =
                genre.trim()
                        .toLowerCase(Locale.ROOT);

        String escapedGenre =
                escapeLike(normalizedGenre);

        return (root, query, cb) ->
                cb.like(
                        cb.lower(
                                root
                                        .get("film")
                                        .get("genresText")
                        ),
                        "%" + escapedGenre + "%",
                        '\\'
                );
    }


    private static String escapeLike(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

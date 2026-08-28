package com.example.omdb.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FilmDetail {

    private final String imdbId;
    private final String title;
    private final String yearText;
    private final Integer releaseYear;
    private final LocalDate releasedOn;
    private final Integer runtimeMinutes;
    private final String genre;
    private final String director;
    private final String actors;
    private final String plot;
    private final String posterUrl;
    private final BigDecimal imdbRating;
    private final String type;

    public FilmDetail(
            String imdbId,
            String title,
            String yearText,
            Integer releaseYear,
            LocalDate releasedOn,
            Integer runtimeMinutes,
            String genre,
            String director,
            String actors,
            String plot,
            String posterUrl,
            BigDecimal imdbRating,
            String type
    ) {
        this.imdbId = imdbId;
        this.title = title;
        this.yearText = yearText;
        this.releaseYear = releaseYear;
        this.releasedOn = releasedOn;
        this.runtimeMinutes = runtimeMinutes;
        this.genre = genre;
        this.director = director;
        this.actors = actors;
        this.plot = plot;
        this.posterUrl = posterUrl;
        this.imdbRating = imdbRating;
        this.type = type;
    }

    public String getImdbId() {
        return imdbId;
    }

    public String getTitle() {
        return title;
    }

    public String getYearText() {
        return yearText;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public LocalDate getReleasedOn() {
        return releasedOn;
    }

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public String getGenre() {
        return genre;
    }

    public String getDirector() {
        return director;
    }

    public String getActors() {
        return actors;
    }

    public java.util.List<String> getActorNames() { return names(actors); }
    public java.util.List<String> getDirectorNames() { return names(director); }
    private static java.util.List<String> names(String value) {
        if (value == null) return java.util.List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim)
                .filter(name -> !name.isBlank() && !"N/A".equalsIgnoreCase(name)).distinct().toList();
    }

    public String getPlot() {
        return plot;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public BigDecimal getImdbRating() {
        return imdbRating;
    }

    public String getType() {
        return type;
    }
}

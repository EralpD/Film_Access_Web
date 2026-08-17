package com.example.film;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "films",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_films_imdb_id",
            columnNames = "imdb_id"
        )
    }
)
public class Film {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imdb_id", nullable = false, length = 20)
    private String imdbId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "year_text", length = 30)
    private String yearText;

    @Column(name = "release_year")
    private Short releaseYear;

    @Column(length = 20)
    private String type;

    @Column(name = "released_on")
    private LocalDate releasedOn;

    @Column(name = "runtime_minutes")
    private Short runtimeMinutes;

    @Column(name = "genres_text", length = 500)
    private String genresText;

    @Column(length = 500)
    private String director;

    @Column(length = 1000)
    private String actors;

    @Column(columnDefinition = "TEXT")
    private String plot;

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "imdb_rating", precision = 3, scale = 1)
    private BigDecimal imdbRating;

    @Column(name = "fetched_at")
    private OffsetDateTime fetchedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Film() {
    }

    public static Film createFromOmdb(
        String imdbId,
        String title,
        String yearText,
        Short releaseYear,
        String type
    ) {

        Film film = new Film();

        OffsetDateTime now =
                OffsetDateTime.now();

        film.imdbId = imdbId;
        film.title = title;
        film.yearText = yearText;
        film.releaseYear = releaseYear;
        film.type = type;

        film.fetchedAt = now;
        film.createdAt = now;
        film.updatedAt = now;

        return film;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getYearText() {
        return yearText;
    }

    public void setYearText(String yearText) {
        this.yearText = yearText;
    }

    public Short getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Short releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDate getReleasedOn() {
        return releasedOn;
    }

    public void setReleasedOn(LocalDate releasedOn) {
        this.releasedOn = releasedOn;
    }

    public Short getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Short runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getGenresText() {
        return genresText;
    }

    public void setGenresText(String genresText) {
        this.genresText = genresText;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getActors() {
        return actors;
    }

    public void setActors(String actors) {
        this.actors = actors;
    }

    public String getPlot() {
        return plot;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public BigDecimal getImdbRating() {
        return imdbRating;
    }

    public void setImdbRating(BigDecimal imdbRating) {
        this.imdbRating = imdbRating;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(OffsetDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


}
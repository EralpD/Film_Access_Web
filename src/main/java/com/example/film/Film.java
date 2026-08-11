package com.example.film;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
}
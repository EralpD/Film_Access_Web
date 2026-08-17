package com.example.archive;

import java.time.OffsetDateTime;

import com.example.film.Film;
import com.example.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "user_films",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_films_user_film",
            columnNames = {"user_id", "film_id"}
        )
    }
)
public class UserFilm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Lazy, because don't want to load all of user data when specific one requested
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Lazy, because don't want to load all of film data when specific one requested
    @JoinColumn(name = "film_id", nullable = false)
    private Film film;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    protected UserFilm() {
    }

    public UserFilm(User user, Film film, OffsetDateTime addedAt){
        this.user = user;
        this.film = film;
        this.addedAt = addedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Film getFilm() {
        return film;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setFilm(Film film) {
        this.film = film;
    }

    public void setAddedAt(OffsetDateTime addedAt) {
        this.addedAt = addedAt;
    }
}


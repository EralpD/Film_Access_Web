package com.example.archive;

import com.example.film.Film;
import com.example.user.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "film_id", nullable = false)
    private Film film;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    protected UserFilm() {
    }
}


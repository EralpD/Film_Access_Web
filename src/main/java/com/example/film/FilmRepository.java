package com.example.film;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilmRepository extends JpaRepository<Film, Long> {

    Optional<Film> findByImdbId(String imdbId);

    boolean existsByImdbId(String imdbId);
}

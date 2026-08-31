package com.example.film;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilmRepository extends JpaRepository<Film, Long> {

    Optional<Film> findByImdbId(String imdbId);

    boolean existsByImdbId(String imdbId);

    @Query("select f.imdbId from Film f where f.imdbId in :imdbIds")
    Set<String> findExistingImdbIds(@Param("imdbIds") Collection<String> imdbIds);
}

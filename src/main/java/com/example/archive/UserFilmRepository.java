package com.example.archive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFilmRepository extends JpaRepository<UserFilm, Long> {

    boolean existsByUserIdAndFilmId(Long userId, Long filmId);

    List<UserFilm> findByUserId(Long userId);
}


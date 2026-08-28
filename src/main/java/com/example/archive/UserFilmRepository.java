package com.example.archive;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;


public interface UserFilmRepository extends JpaRepository<UserFilm, Long>, JpaSpecificationExecutor<UserFilm> {

    boolean existsByUserIdAndFilmId(Long userId, Long filmId);

    boolean existsByUser_IdAndFilm_ImdbId(
        Long userId,
        String imdbId
    );

    List<UserFilm> findByUserId(Long userId);

    Optional<UserFilm> findByIdAndUser_Id(
        Long id,
        Long userId
    );

    @EntityGraph(attributePaths = "film")
    List<UserFilm> findByIdInAndUser_Id(
        List<Long> ids,
        Long userId
    );

    @EntityGraph(attributePaths = "film")
    List<UserFilm> findAllByUser_IdOrderByAddedAtDesc(Long userId);

    @Override
    @EntityGraph(attributePaths = "film")
    Page<UserFilm> findAll(Specification<UserFilm> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths= "film")
    List<UserFilm> findAll(
        Specification<UserFilm> specification,
        Sort sort
    );

    @EntityGraph(attributePaths = "film")
    List<UserFilm> findByIdIn(Collection<Long> ids);
}


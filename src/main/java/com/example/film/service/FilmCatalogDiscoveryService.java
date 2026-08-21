package com.example.film.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.archive.mapper.FilmCatalogMapper;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.search.service.FilmSemanticIndexService;

@Service
public class FilmCatalogDiscoveryService {

    private static final Pattern IMDB_ID_PATTERN =
            Pattern.compile("^tt\\d+$");

    private static final Logger log =
            LoggerFactory.getLogger(
                    FilmCatalogDiscoveryService.class
            );

    private final FilmRepository filmRepository;
    private final FilmCatalogMapper filmCatalogMapper;
    private final FilmSemanticIndexService semanticIndexService;
    private final OmdbFilmDetailService filmDetailService;
    private final Duration detailCacheTtl;


    private final ConcurrentMap<String, Object> discoveryLocks =
            new ConcurrentHashMap<>();


    public FilmCatalogDiscoveryService(
            FilmRepository filmRepository,
            FilmCatalogMapper filmCatalogMapper,
            FilmSemanticIndexService semanticIndexService,
            OmdbFilmDetailService filmDetailService,
            @Value("${app.catalog.detail-cache-ttl:30d}")
            Duration detailCacheTtl
    ) {
        this.filmRepository = filmRepository;
        this.filmCatalogMapper = filmCatalogMapper;
        this.semanticIndexService = semanticIndexService;
        this.filmDetailService = filmDetailService;
        this.detailCacheTtl = detailCacheTtl;
    }


    public FilmDetail getFilmDetail(String imdbId) {
        return filmCatalogMapper.toFilmDetail(
                getOrFetchFilm(imdbId)
        );
    }


    public Film getOrFetchFilm(String imdbId) {

        String normalizedImdbId = normalizeImdbId(imdbId);

        Optional<Film> cached =
                filmRepository.findByImdbId(normalizedImdbId);

        if (cached.isPresent()
                && isFresh(cached.get())) {

            Film film = cached.get();
            ensureIndexedSafely(film);
            return film;
        }

        Object lock = discoveryLocks.computeIfAbsent(
                normalizedImdbId,
                ignored -> new Object()
        );

        try {
            synchronized (lock) {
                cached = filmRepository.findByImdbId(
                        normalizedImdbId
                );

                if (cached.isPresent()
                        && isFresh(cached.get())) {

                    Film film = cached.get();
                    ensureIndexedSafely(film);
                    return film;
                }

                FilmDetail detail =
                        filmDetailService.getFilmDetail(
                                normalizedImdbId
                        );

                return upsertAndIndex(detail);
            }
        } finally {
            discoveryLocks.remove(normalizedImdbId, lock);
        }
    }


    public Film catalogViewedFilm(FilmDetail detail) {

        validateDetail(detail);

        String imdbId = normalizeImdbId(detail.getImdbId());

        Object lock = discoveryLocks.computeIfAbsent(
                imdbId,
                ignored -> new Object()
        );

        try {
            synchronized (lock) {
                return upsertAndIndex(detail);
            }
        } finally {
            discoveryLocks.remove(imdbId, lock);
        }
    }


    private Film upsertAndIndex(FilmDetail detail) {

        validateDetail(detail);

        Optional<Film> existing =
                filmRepository.findByImdbId(detail.getImdbId());

        Film savedFilm;

        if (existing.isPresent()) {
            Film film = existing.get();

            filmCatalogMapper.updateFilm(film, detail);
            savedFilm = filmRepository.saveAndFlush(film);

        } else {
            Film newFilm = filmCatalogMapper.toFilm(detail);

            try {
                savedFilm = filmRepository.saveAndFlush(newFilm);

            } catch (DataIntegrityViolationException exception) {
                /*
                 * Birden fazla uygulama instance'ı aynı filmi aynı anda
                 * eklerse benzersiz IMDb kısıtı kazanır; mevcut kayıt kullanılır.
                 */
                savedFilm = filmRepository.findByImdbId(
                                detail.getImdbId()
                        )
                        .orElseThrow(() -> exception);

                filmCatalogMapper.updateFilm(savedFilm, detail);
                savedFilm = filmRepository.saveAndFlush(savedFilm);
            }
        }

        ensureIndexedSafely(savedFilm);
        return savedFilm;
    }


    private void ensureIndexedSafely(Film film) {
        try {
            semanticIndexService.indexFilm(film);

        } catch (RuntimeException exception) {
            log.warn(
                    "Embedding could not be generated for film: {}",
                    film.getImdbId(),
                    exception
            );
        }
    }


    private boolean isFresh(Film film) {

        OffsetDateTime fetchedAt = film.getFetchedAt();

        if (fetchedAt == null
                || detailCacheTtl.isNegative()
                || detailCacheTtl.isZero()) {
            return false;
        }

        return fetchedAt.isAfter(
                OffsetDateTime.now().minus(detailCacheTtl)
        );
    }


    private void validateDetail(FilmDetail detail) {
        if (detail == null) {
            throw new IllegalArgumentException(
                    "Film detail cannot be null."
            );
        }

        normalizeImdbId(detail.getImdbId());
    }


    private String normalizeImdbId(String imdbId) {

        if (imdbId == null) {
            throw new IllegalArgumentException(
                    "IMDb ID cannot be empty."
            );
        }

        String normalized = imdbId.trim();

        if (!IMDB_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid IMDb ID."
            );
        }

        return normalized;
    }
}

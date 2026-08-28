package com.example.film.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.archive.mapper.FilmCatalogMapper;
import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;

@Service
public class FilmCatalogDiscoveryService {

    private static final Pattern IMDB_ID_PATTERN =
            Pattern.compile("^tt\\d+$");

    private final FilmRepository filmRepository;
    private final FilmCatalogMapper filmCatalogMapper;
    private final FilmMaintenanceQueue maintenanceQueue;
    private final OmdbFilmDetailService filmDetailService;
    private final Duration detailCacheTtl;


    // Bounded stable locks: removing per-ID locks while other callers wait can create duplicate fetches.
    private final Object[] discoveryLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(ignored -> new Object()).toArray();


    public FilmCatalogDiscoveryService(
            FilmRepository filmRepository,
            FilmCatalogMapper filmCatalogMapper,
            FilmMaintenanceQueue maintenanceQueue,
            OmdbFilmDetailService filmDetailService,
            @Value("${app.catalog.detail-cache-ttl:30d}")
            Duration detailCacheTtl
    ) {
        this.filmRepository = filmRepository;
        this.filmCatalogMapper = filmCatalogMapper;
        this.maintenanceQueue = maintenanceQueue;
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

        if (cached.isPresent()) {

            Film film = cached.get();
            maintenanceQueue.request(film, !isFresh(film));
            return film;
        }

        Object lock = discoveryLocks[Math.floorMod(normalizedImdbId.hashCode(), discoveryLocks.length)];
            synchronized (lock) {
                cached = filmRepository.findByImdbId(
                        normalizedImdbId
                );

                if (cached.isPresent()) {

                    Film film = cached.get();
                    maintenanceQueue.request(film, !isFresh(film));
                    return film;
                }

                FilmDetail detail =
                        filmDetailService.getFilmDetail(
                                normalizedImdbId
                        );

                return upsertAndQueue(detail);
            }
    }


    public Film catalogViewedFilm(FilmDetail detail) {

        validateDetail(detail);

        String imdbId = normalizeImdbId(detail.getImdbId());

        Object lock = discoveryLocks[Math.floorMod(imdbId.hashCode(), discoveryLocks.length)];
            synchronized (lock) {
                return upsertAndQueue(detail);
            }
    }

    /** Only the background metadata worker forces a refresh of an existing cached film. */
    public Film refreshMetadata(String imdbId) {
        String normalized = normalizeImdbId(imdbId);
        FilmDetail detail = filmDetailService.getFilmDetail(normalized);
        if (detail == null || !normalized.equals(detail.getImdbId()))
            throw new IllegalStateException("Film metadata response did not match the requested title.");
        return catalogViewedFilm(detail);
    }


    private Film upsertAndQueue(FilmDetail detail) {

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

        maintenanceQueue.request(savedFilm, false);
        return savedFilm;
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

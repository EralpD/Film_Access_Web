package com.example.film;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class FilmRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private FilmRepository filmRepository;

    @Test
    void shouldSaveAndFindFilmByImdbId() {

        Film film = createFilm(
                "tt1375666",
                "Inception",
                (short) 2010
        );

        Film savedFilm = filmRepository.saveAndFlush(film);

        var result =
                filmRepository.findByImdbId("tt1375666");

        assertThat(result).isPresent();

        assertThat(
                ReflectionTestUtils.getField(savedFilm, "id")
        ).isNotNull();

        assertThat(
                ReflectionTestUtils.getField(
                        result.get(),
                        "title"
                )
        ).isEqualTo("Inception");

        assertThat(
                ReflectionTestUtils.getField(
                        result.get(),
                        "releaseYear"
                )
        ).isEqualTo((short) 2010);
    }

    @Test
    void shouldReturnEmptyWhenImdbIdDoesNotExist() {

        var result =
                filmRepository.findByImdbId("tt0000000");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenImdbIdExists() {

        Film film = createFilm(
                "tt0816692",
                "Interstellar",
                (short) 2014
        );

        filmRepository.saveAndFlush(film);

        boolean exists =
                filmRepository.existsByImdbId(
                        "tt0816692"
                );

        assertThat(exists).isTrue();
    }

    @Test
    void shouldNotAllowDuplicateImdbId() {

        Film first = createFilm(
                "tt1375666",
                "Inception",
                (short) 2010
        );

        Film duplicate = createFilm(
                "tt1375666",
                "Duplicate Inception",
                (short) 2010
        );

        filmRepository.saveAndFlush(first);

        assertThatThrownBy(() ->
                filmRepository.saveAndFlush(duplicate)
        ).isInstanceOf(Exception.class);
    }

    private Film createFilm(
            String imdbId,
            String title,
            short releaseYear
    ) {

        Film film = new Film();

        ReflectionTestUtils.setField(
                film,
                "imdbId",
                imdbId
        );

        ReflectionTestUtils.setField(
                film,
                "title",
                title
        );

        ReflectionTestUtils.setField(
                film,
                "yearText",
                String.valueOf(releaseYear)
        );

        ReflectionTestUtils.setField(
                film,
                "releaseYear",
                releaseYear
        );

        ReflectionTestUtils.setField(
                film,
                "type",
                "MOVIE"
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        ReflectionTestUtils.setField(
                film,
                "fetchedAt",
                now
        );

        ReflectionTestUtils.setField(
                film,
                "createdAt",
                now
        );

        ReflectionTestUtils.setField(
                film,
                "updatedAt",
                now
        );

        return film;
    }
}

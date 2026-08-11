package com.example.archive;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.film.Film;
import com.example.film.FilmRepository;
import com.example.user.User;
import com.example.user.UserRepository;

@Testcontainers
@DataJpaTest
class UserFilmRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

        @Autowired private 
        UserRepository userRepository; 

        @Autowired private 
        FilmRepository filmRepository; 

        @Autowired private 
        UserFilmRepository userFilmRepository; 

        @Autowired private 
        JdbcTemplate jdbcTemplate; 


    @Test
    void shouldSaveUserFilmRelationship() {

        User user = userRepository.saveAndFlush(
                createUser(
                        "user@example.com",
                        "Test User"
                )
        );

        Film film = filmRepository.saveAndFlush(
                createFilm(
                        "tt1375666",
                        "Inception"
                )
        );

        UserFilm userFilm =
                createUserFilm(user, film);

        UserFilm saved =
                userFilmRepository.saveAndFlush(
                        userFilm
                );

        assertThat(
                ReflectionTestUtils.getField(
                        saved,
                        "id"
                )
        ).isNotNull();

        Long userId = (Long)
                ReflectionTestUtils.getField(
                        user,
                        "id"
                );

        Long filmId = (Long)
                ReflectionTestUtils.getField(
                        film,
                        "id"
                );

        assertThat(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                userId,
                                filmId
                        )
        ).isTrue();
    }

    @Test
    void shouldFindFilmsBelongingToUser() {

        User user = userRepository.saveAndFlush(
                createUser(
                        "user@example.com",
                        "Test User"
                )
        );

        Film inception =
                filmRepository.saveAndFlush(
                        createFilm(
                                "tt1375666",
                                "Inception"
                        )
                );

        Film interstellar =
                filmRepository.saveAndFlush(
                        createFilm(
                                "tt0816692",
                                "Interstellar"
                        )
                );

        userFilmRepository.saveAndFlush(
                createUserFilm(
                        user,
                        inception
                )
        );

        userFilmRepository.saveAndFlush(
                createUserFilm(
                        user,
                        interstellar
                )
        );

        Long userId = (Long)
                ReflectionTestUtils.getField(
                        user,
                        "id"
                );

        var results =
                userFilmRepository.findByUserId(
                        userId
                );

        assertThat(results).hasSize(2);

        var titles = results.stream()
                .map(userFilm ->
                        (Film) ReflectionTestUtils
                                .getField(
                                        userFilm,
                                        "film"
                                )
                )
                .map(film ->
                        (String) ReflectionTestUtils
                                .getField(
                                        film,
                                        "title"
                                )
                )
                .toList();

        assertThat(titles)
                .containsExactlyInAnyOrder(
                        "Inception",
                        "Interstellar"
                );
    }

    @Test
    void shouldNotAllowSameFilmTwiceForSameUser() {

        User user = userRepository.saveAndFlush(
                createUser(
                        "user@example.com",
                        "Test User"
                )
        );

        Film film = filmRepository.saveAndFlush(
                createFilm(
                        "tt1375666",
                        "Inception"
                )
        );

        UserFilm first =
                createUserFilm(user, film);

        UserFilm duplicate =
                createUserFilm(user, film);

        userFilmRepository.saveAndFlush(first);

        assertThatThrownBy(() ->
                userFilmRepository
                        .saveAndFlush(duplicate)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void shouldAllowSameFilmForDifferentUsers() {

        User firstUser =
                userRepository.saveAndFlush(
                        createUser(
                                "first@example.com",
                                "First User"
                        )
                );

        User secondUser =
                userRepository.saveAndFlush(
                        createUser(
                                "second@example.com",
                                "Second User"
                        )
                );

        Film film =
                filmRepository.saveAndFlush(
                        createFilm(
                                "tt1375666",
                                "Inception"
                        )
                );

        userFilmRepository.saveAndFlush(
                createUserFilm(
                        firstUser,
                        film
                )
        );

        userFilmRepository.saveAndFlush(
                createUserFilm(
                        secondUser,
                        film
                )
        );

        Long firstUserId =
                (Long) ReflectionTestUtils.getField(
                        firstUser,
                        "id"
                );

        Long secondUserId =
                (Long) ReflectionTestUtils.getField(
                        secondUser,
                        "id"
                );

        Long filmId =
                (Long) ReflectionTestUtils.getField(
                        film,
                        "id"
                );

        assertThat(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                firstUserId,
                                filmId
                        )
        ).isTrue();

        assertThat(
                userFilmRepository
                        .existsByUserIdAndFilmId(
                                secondUserId,
                                filmId
                        )
        ).isTrue();
    }


     @Test
        void shouldDeleteUserFilmsButKeepFilmWhenUserIsDeleted() {

        User user = userRepository.saveAndFlush(
                createUser(
                        "cascade@example.com",
                        "Cascade User"
                )
        );

        Film film = filmRepository.saveAndFlush(
                createFilm(
                        "tt1375666",
                        "Inception"
                )
        );

        userFilmRepository.saveAndFlush(
                createUserFilm(user, film)
        );

        Long userId = (Long) ReflectionTestUtils.getField(
                user,
                "id"
        );

        Long filmId = (Long) ReflectionTestUtils.getField(
                film,
                "id"
        );

        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId
        );

        Integer userFilmCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_films
                WHERE user_id = ?
                """,
                Integer.class,
                userId
        );

        Integer filmCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM films
                WHERE id = ?
                """,
                Integer.class,
                filmId
        );

        assertThat(userFilmCount).isZero();
        assertThat(filmCount).isEqualTo(1);
        }




    private Film createFilm(
            String imdbId,
            String title
    ) {

        Film film =
                instantiate(Film.class);

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

    private UserFilm createUserFilm(
            User user,
            Film film
    ) {

        UserFilm userFilm =
                new UserFilm();

        ReflectionTestUtils.setField(
                userFilm,
                "user",
                user
        );

        ReflectionTestUtils.setField(
                userFilm,
                "film",
                film
        );

        ReflectionTestUtils.setField(
                userFilm,
                "addedAt",
                OffsetDateTime.now()
        );

        return userFilm;
    }

    private User createUser(
            String email,
            String displayName
    ) {

        User user =
                instantiate(User.class);

        ReflectionTestUtils.setField(
                user,
                "email",
                email
        );

        ReflectionTestUtils.setField(
                user,
                "displayName",
                displayName
        );

        ReflectionTestUtils.setField(
                user,
                "passwordHash",
                "hashed-password"
        );

        ReflectionTestUtils.setField(
                user,
                "role",
                "USER"
        );

        ReflectionTestUtils.setField(
                user,
                "enabled",
                true
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        ReflectionTestUtils.setField(
                user,
                "createdAt",
                now
        );

        ReflectionTestUtils.setField(
                user,
                "updatedAt",
                now
        );

        return user;
    }

    private <T> T instantiate(
            Class<T> type
    ) {

        try {
            Constructor<T> constructor =
                    type.getDeclaredConstructor();

            constructor.setAccessible(true);

            return constructor.newInstance();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not instantiate "
                            + type.getSimpleName(),
                    exception
            );
        }
    }
}

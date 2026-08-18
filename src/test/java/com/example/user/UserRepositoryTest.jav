package com.example.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.utility.DockerImageName; 

@Testcontainers
@DataJpaTest
class UserRepositoryTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
                                                                         .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUserByEmail() {

        User user = new User();

        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        userRepository.save(user);

        var result =
                userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();

        assertThat(result.get().getDisplayName())
                .isEqualTo("Test User");

    }

    @Test
    void shouldNotAllowUserWithoutEmail() {

        User user = createUser(
                        null,
                        "Test User");

        assertThatThrownBy(() ->
                        userRepository.saveAndFlush(user)
                ).isInstanceOf(Exception.class);
        }
    @Test
        void shouldNotAllowUserWithoutEmail() {

        User user = new User();

        ReflectionTestUtils.setField(
                user,
                "displayName",
                "Test User"
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

        OffsetDateTime now = OffsetDateTime.now();

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

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(user)
        ).isInstanceOf(Exception.class);
        }

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void shouldRejectUserFilmWithUnknownUser() {

        Film film = filmRepository.saveAndFlush(
                createFilm(
                        "tt1375666",
                        "Inception"
                )
        );

        Long filmId =
                (Long) ReflectionTestUtils.getField(
                        film,
                        "id"
                );

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        INSERT INTO user_films (
                                user_id,
                                film_id,
                                added_at
                        )
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """,
                        999999L,
                        filmId
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
        }

        
}

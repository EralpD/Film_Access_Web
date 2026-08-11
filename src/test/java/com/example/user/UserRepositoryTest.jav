package com.example.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

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
}

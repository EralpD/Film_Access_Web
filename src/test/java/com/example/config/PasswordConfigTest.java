package com.example.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        PasswordConfig passwordConfig = new PasswordConfig();
        passwordEncoder = passwordConfig.passwordEncoder();
    }

    @Test
    void shouldEncodePassword() {

        String rawPassword = "StrongPassword123!";

        String encodedPassword =
                passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
    }

    @Test
    void shouldMatchCorrectPassword() {

        String rawPassword = "StrongPassword123!";

        String encodedPassword =
                passwordEncoder.encode(rawPassword);

        assertTrue(
                passwordEncoder.matches(
                        rawPassword,
                        encodedPassword
                )
        );
    }

    @Test
    void shouldRejectIncorrectPassword() {

        String encodedPassword =
                passwordEncoder.encode(
                        "CorrectPassword123!"
                );

        assertFalse(
                passwordEncoder.matches(
                        "WrongPassword123!",
                        encodedPassword
                )
        );
    }
}

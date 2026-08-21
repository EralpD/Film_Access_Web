package com.example.user.security;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.user.User;
import com.example.user.UserRepository;
import com.example.user.UserRole;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {

        userRepository =
                mock(UserRepository.class);

        userDetailsService =
                new CustomUserDetailsService(
                        userRepository
                );
    }

    @Test
    void shouldLoadUserByEmail() {

        User user = mock(User.class);

        when(user.getEmail())
                .thenReturn("user@example.com");

        when(user.getPasswordHash())
                .thenReturn("{bcrypt}encoded-password");

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(user.isEnabled())
                .thenReturn(true);

        when(
                userRepository.findByEmail(
                        "user@example.com"
                )
        ).thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(
                        "user@example.com"
                );

        assertEquals(
                "user@example.com",
                userDetails.getUsername()
        );

        assertEquals(
                "{bcrypt}encoded-password",
                userDetails.getPassword()
        );

        verify(userRepository)
                .findByEmail("user@example.com");
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {

        when(
                userRepository.findByEmail(
                        "missing@example.com"
                )
        ).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () ->
                        userDetailsService
                                .loadUserByUsername(
                                        "missing@example.com"
                                )
        );

        verify(userRepository)
                .findByEmail("missing@example.com");
    }
}

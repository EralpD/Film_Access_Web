package com.example.user.security;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.core.GrantedAuthority;

import com.example.user.User;

class CustomUserDetailsTest {

    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {

        user = mock(User.class);

        when(user.getEmail())
                .thenReturn("user@example.com");

        when(user.getPasswordHash())
                .thenReturn("{bcrypt}encoded-password");

        when(user.getRole())
                .thenReturn("USER");

        when(user.isEnabled())
                .thenReturn(true);

        userDetails = new CustomUserDetails(user);
    }

    @Test
    void shouldReturnEmailAsUsername() {

        assertEquals(
                "user@example.com",
                userDetails.getUsername()
        );
    }

    @Test
    void shouldReturnPasswordHash() {

        assertEquals(
                "{bcrypt}encoded-password",
                userDetails.getPassword()
        );
    }

    @Test
    void shouldReturnUserRoleAsAuthority() {

        Collection<? extends GrantedAuthority> authorities =
                userDetails.getAuthorities();

        assertEquals(1, authorities.size());

        assertTrue(
                authorities.stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_USER")
                        )
        );
    }

    @Test
    void shouldReturnEnabledState() {

        assertTrue(userDetails.isEnabled());
    }

    @Test
    void shouldReturnDisabledState() {

        when(user.isEnabled()).thenReturn(false);

        CustomUserDetails disabledUserDetails =
                new CustomUserDetails(user);

        assertFalse(disabledUserDetails.isEnabled());
    }
}

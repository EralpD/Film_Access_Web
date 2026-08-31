package com.example.user.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.example.archive.UserFilm;
import com.example.archive.UserFilmRepository;
import com.example.film.Film;
import com.example.user.User;
import com.example.user.UserRepository;
import com.example.user.UserRole;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserFilmRepository userFilmRepository;

    UserProfileService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, userFilmRepository);
        user = new User();
        user.setId(42L);
        user.setEmail("viewer@example.com");
        user.setDisplayName("Film Viewer");
        user.setRole(UserRole.USER);
        user.setCreatedAt(OffsetDateTime.parse("2025-02-03T10:15:30+03:00"));
    }

    @Test
    void buildsVisibleProfileAndGenreDistribution() {
        Film first = Film.createFromOmdb("tt0000001", "First", "2020", (short) 2020, "movie");
        first.setGenresText("Action, Drama");
        Film second = Film.createFromOmdb("tt0000002", "Second", "2021", (short) 2021, "movie");
        second.setGenresText("Comedy, Drama");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userFilmRepository.findAllByUser_IdOrderByAddedAtDesc(42L)).thenReturn(List.of(
                new UserFilm(user, first, OffsetDateTime.now()),
                new UserFilm(user, second, OffsetDateTime.now())
        ));

        UserProfileResponse response = service.getProfile(user.getEmail(), 42L);

        assertThat(response.displayName()).isEqualTo("Film Viewer");
        assertThat(response.memberSince()).isEqualTo("February 3, 2025");
        assertThat(response.filmCount()).isEqualTo(2);
        assertThat(response.genreTagCount()).isEqualTo(4);
        assertThat(response.genres()).extracting(GenreBreakdownItem::name)
                .containsExactly("Drama", "Action", "Comedy");
        assertThat(response.genres().getFirst().count()).isEqualTo(2);
        assertThat(response.genres().getFirst().percentage()).isEqualTo(50.0);
        assertThat(response.genres().get(1).color()).isEqualTo("#ef6351");
    }

    @Test
    void rejectsAUserIdThatDoesNotBelongToTheSession() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getProfile(user.getEmail(), 99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(userFilmRepository, never()).findAllByUser_IdOrderByAddedAtDesc(42L);
    }
}

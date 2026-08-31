package com.example.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.user.Dto.RegistrationRequest;
import com.example.user.User;
import com.example.user.UserRepository;

class UserRegistrationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {

        userRepository =
                mock(UserRepository.class);

        passwordEncoder =
                mock(PasswordEncoder.class);

        registrationService =
                new UserRegistrationService(
                        userRepository,
                        passwordEncoder
                );
    }

    @Test
    void shouldRegisterValidUser() {

        RegistrationRequest request =
                validRequest();

        when(
                userRepository.existsByEmail(
                        "eralp@example.com"
                )
        ).thenReturn(false);

        when(
                passwordEncoder.encode(
                        "StrongPassword123!"
                )
        ).thenReturn("{bcrypt}encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        registrationService.register(request);

        verify(userRepository)
                .existsByEmail("eralp@example.com");

        verify(passwordEncoder)
                .encode("StrongPassword123!");

        verify(userRepository)
                .save(any(User.class));
    }

    @Test
    void shouldNormalizeEmailBeforeSaving() {

        RegistrationRequest request =
                validRequest();

        request.setEmail(
                "   ERALP@EXAMPLE.COM   "
        );

        when(
                userRepository.existsByEmail(
                        "eralp@example.com"
                )
        ).thenReturn(false);

        when(
                passwordEncoder.encode(any())
        ).thenReturn("{bcrypt}encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        registrationService.register(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(
                captor.capture()
        );

        User savedUser = captor.getValue();

        assertEquals(
                "eralp@example.com",
                savedUser.getEmail()
        );
    }

    @Test
    void shouldStoreEncodedPasswordInsteadOfRawPassword() {

        RegistrationRequest request =
                validRequest();

        when(
                userRepository.existsByEmail(
                        "eralp@example.com"
                )
        ).thenReturn(false);

        when(
                passwordEncoder.encode(
                        "StrongPassword123!"
                )
        ).thenReturn("{bcrypt}encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        registrationService.register(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(
                captor.capture()
        );

        User savedUser = captor.getValue();

        assertEquals(
                "{bcrypt}encoded-password",
                savedUser.getPasswordHash()
        );
    }

    @Test
    void shouldCreateUserWithDefaultRoleAndEnabledState() {

        RegistrationRequest request =
                validRequest();

        when(
                userRepository.existsByEmail(
                        "eralp@example.com"
                )
        ).thenReturn(false);

        when(
                passwordEncoder.encode(any())
        ).thenReturn("{bcrypt}encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        registrationService.register(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(
                captor.capture()
        );

        User savedUser = captor.getValue();

        assertEquals(
                com.example.user.UserRole.USER,
                savedUser.getRole()
        );

        assertTrue(
                savedUser.isEnabled()
        );
    }

    @Test
    void shouldRejectRegistrationWhenPasswordsDoNotMatch() {

        RegistrationRequest request =
                validRequest();

        request.setPasswordConfirmation(
                "DifferentPassword123!"
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        registrationService
                                .register(request)
        );

        verify(
                userRepository,
                never()
        ).save(any(User.class));
    }

    @Test
    void shouldRejectRegistrationWhenEmailAlreadyExists() {

        RegistrationRequest request =
                validRequest();

        when(
                userRepository.existsByEmail(
                        "eralp@example.com"
                )
        ).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        registrationService
                                .register(request)
        );

        verify(
                passwordEncoder,
                never()
        ).encode(any());

        verify(
                userRepository,
                never()
        ).save(any(User.class));
    }

    private RegistrationRequest validRequest() {

        RegistrationRequest request =
                new RegistrationRequest();

        request.setDisplayName("Eralp");
        request.setEmail("eralp@example.com");
        request.setPassword("StrongPassword123!");
        request.setPasswordConfirmation(
                "StrongPassword123!"
        );

        return request;
    }
}

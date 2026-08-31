package com.example.user.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.user.User;
import com.example.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceTest {

    @Mock UserRepository userRepository;

    @Test
    void deactivatesOnlyTheAuthenticatedAccount() {
        User user = new User();
        user.setEmail("viewer@example.com");
        user.setEnabled(true);
        when(userRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(user));

        new AccountSettingsService(userRepository).deactivateAccount("viewer@example.com");

        assertThat(user.isEnabled()).isFalse();
        verify(userRepository).save(user);
    }
}

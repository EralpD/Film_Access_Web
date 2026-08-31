package com.example.user.settings;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.user.User;
import com.example.user.UserRepository;

@Service
public class AccountSettingsService {

    private final UserRepository userRepository;

    public AccountSettingsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void deactivateAccount(String authenticatedEmail) {
        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."));

        user.setEnabled(false);
        userRepository.save(user);
    }
}

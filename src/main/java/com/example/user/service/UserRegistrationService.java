package com.example.user.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.user.Dto.RegistrationRequest;
import com.example.user.Dto.UserResponse;
import com.example.user.User;
import com.example.user.UserRepository;
import com.example.user.UserRole;

import jakarta.transaction.Transactional;

@Service
public class UserRegistrationService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private String normalizeEmail(String email){
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordConfirmation(RegistrationRequest request){
        if(!request.getPassword().equals(request.getPasswordConfirmation())){
            throw new IllegalArgumentException("Passwords do not match.");
        }
    }

    private void ensureEmailIsAvailable(String email){
        if (userRepository.existsByEmail(email)){
            throw new IllegalArgumentException("Email is already in use.");
        }
    }

    private String encodePassword(String rawPassword){
        return passwordEncoder.encode(rawPassword);
    }

    private User createUser(RegistrationRequest request, String normalizedEmail, String encodedPassword){

        User user = new User();

        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(encodedPassword);
        user.setRole(UserRole.USER);
        user.setEnabled(true);

        return user;
    }

    private UserResponse toResponse(User user){
        return new UserResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail()
        );
    }

    @Transactional
    public UserResponse register(RegistrationRequest request) {
        validatePasswordConfirmation(request);

        String normalizedEmail = normalizeEmail(request.getEmail());

        ensureEmailIsAvailable(normalizedEmail);

        String encodedPassword = encodePassword(request.getPassword());

        User user = createUser(
            request,
            normalizedEmail,
            encodedPassword
        );

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }    

}

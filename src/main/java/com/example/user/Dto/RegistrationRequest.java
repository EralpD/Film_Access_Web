package com.example.user.Dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {

    @NotBlank(message = "Display name cannot be empty.")
    @Size(
        min = 2,
        max = 100,
        message = "Display name must be between 2 and 100 characters."
    )
    private String displayName;

    @NotBlank(message = "Email cannot be empty.")
    @Email(message = "Please enter a valid email address.")
    @Size(
        max = 254,
        message = "Email cannot exceed 254 characters."
    )
    private String email;

    @NotBlank(message = "Password cannot be empty.")
    @Size(
        min = 8,
        max = 32,
        message = "Password must be between 8 and 32 characters."
    )
    private String password;

    @NotBlank(message = "Password confirmation cannot be empty.")
    private String passwordConfirmation;

    public RegistrationRequest() {
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirmation() {
        return passwordConfirmation;
    }

    public void setPasswordConfirmation(String passwordConfirmation) {
        this.passwordConfirmation = passwordConfirmation;
    }

    @AssertTrue(message = "Passwords do not match.")
    public boolean isPasswordMatching() {
        if (password == null || passwordConfirmation == null){
            return false;
        }
        return password.equals(passwordConfirmation);
    }
}


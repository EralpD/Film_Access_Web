package com.example.user.Dto;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RegistrationRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory =
                Validation.buildDefaultValidatorFactory();

        validator = factory.getValidator();
    }

    @Test
    void shouldAcceptValidRegistrationRequest() {

        RegistrationRequest request =
                new RegistrationRequest();

        request.setDisplayName("Eralp");
        request.setEmail("eralp@example.com");
        request.setPassword("StrongPassword123!");
        request.setPasswordConfirmation(
                "StrongPassword123!"
        );

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldRejectBlankDisplayName() {

        RegistrationRequest request =
                validRequest();

        request.setDisplayName("");

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldRejectInvalidEmail() {

        RegistrationRequest request =
                validRequest();

        request.setEmail("not-an-email");

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldRejectBlankEmail() {

        RegistrationRequest request =
                validRequest();

        request.setEmail("");

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldRejectShortPassword() {

        RegistrationRequest request =
                validRequest();

        request.setPassword("short");

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldRejectBlankPasswordConfirmation() {

        RegistrationRequest request =
                validRequest();

        request.setPasswordConfirmation("");

        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
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

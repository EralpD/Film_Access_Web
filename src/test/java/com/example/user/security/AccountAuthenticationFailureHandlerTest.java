package com.example.user.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AccountAuthenticationFailureHandlerTest {

    private final AccountAuthenticationFailureHandler handler =
            new AccountAuthenticationFailureHandler();

    @Test
    void disabledAccountsReceiveTheDeletedAccountMessageRoute() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("");

        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        verify(response).sendRedirect("/login?deleted");
    }

    @Test
    void otherAuthenticationFailuresKeepTheGenericErrorRoute() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad credentials"));

        verify(response).sendRedirect("/login?error");
    }
}

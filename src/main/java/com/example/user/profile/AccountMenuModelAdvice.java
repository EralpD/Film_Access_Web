package com.example.user.profile;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.user.security.CustomUserDetails;

@ControllerAdvice
public class AccountMenuModelAdvice {

    @ModelAttribute("accountMenu")
    public AccountMenuView accountMenu(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return new AccountMenuView(
                    userDetails.getUserId(),
                    userDetails.getDisplayName(),
                    userDetails.getUsername()
            );
        }

        return new AccountMenuView(null, authentication.getName(), authentication.getName());
    }
}

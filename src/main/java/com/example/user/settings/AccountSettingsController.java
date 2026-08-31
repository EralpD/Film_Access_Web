package com.example.user.settings;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AccountSettingsController {

    private final AccountSettingsService accountSettingsService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AccountSettingsController(AccountSettingsService accountSettingsService) {
        this.accountSettingsService = accountSettingsService;
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        accountSettingsService.deactivateAccount(authentication.getName());
        logoutHandler.logout(request, response, authentication);
        return "redirect:/login?deleted";
    }
}

package com.example.user.profile;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/details")
    public String details(
            @RequestParam Long userId,
            Authentication authentication,
            Model model
    ) {
        model.addAttribute(
                "profile",
                userProfileService.getProfile(authentication.getName(), userId)
        );
        return "details";
    }
}

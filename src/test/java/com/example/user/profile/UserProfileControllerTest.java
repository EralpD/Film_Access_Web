package com.example.user.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;
import com.example.user.User;
import com.example.user.UserRole;
import com.example.user.security.CustomUserDetails;

@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserProfileService userProfileService;

    @Test
    void detailsRequiresUserIdAndRendersProfileMenuAndChart() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("viewer@example.com");
        user.setDisplayName("Film Viewer");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        user.setEnabled(true);

        var principal = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        var profile = new UserProfileResponse(
                42L,
                "Film Viewer",
                "viewer@example.com",
                "User",
                "February 3, 2025",
                2,
                3,
                List.of(new GenreBreakdownItem("Drama", 2, 66.6667, 0, "66.7%", "#5b8def"))
        );
        when(userProfileService.getProfile("viewer@example.com", 42L)).thenReturn(profile);

        mockMvc.perform(get("/details").param("userId", "42").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/details?userId=42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/settings\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/logout\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Drama")));

        verify(userProfileService).getProfile("viewer@example.com", 42L);
    }

    @Test
    void detailsRejectsRequestsWithoutRequiredUserId() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("viewer@example.com");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        var principal = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        mockMvc.perform(get("/details").with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsReturnsForbiddenWhenUserIdDoesNotMatchTheSession() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("viewer@example.com");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        var principal = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );
        when(userProfileService.getProfile("viewer@example.com", 99L))
                .thenThrow(new AccessDeniedException("Wrong user."));

        mockMvc.perform(get("/details").param("userId", "99").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void detailsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/details").param("userId", "42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

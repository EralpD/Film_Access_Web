package com.example.user.settings;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;

@WebMvcTest(AccountSettingsController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "viewer@example.com")
class AccountSettingsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountSettingsService accountSettingsService;

    @Test
    void settingsContainsOnlyTheAccountDeletionAction() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Delete account")))
                .andExpect(content().string(containsString("/settings/delete-account")));
    }

    @Test
    void deletionRequiresCsrf() throws Exception {
        mockMvc.perform(post("/settings/delete-account"))
                .andExpect(status().isForbidden());

        verify(accountSettingsService, never()).deactivateAccount("viewer@example.com");
    }

    @Test
    void deletionDeactivatesCurrentUserAndLogsOut() throws Exception {
        var result = mockMvc.perform(post("/settings/delete-account").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"))
                .andReturn();

        verify(accountSettingsService).deactivateAccount("viewer@example.com");
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}

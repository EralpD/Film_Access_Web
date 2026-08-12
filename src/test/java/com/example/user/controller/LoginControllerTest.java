package com.example.user.controller;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class LoginControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        LoginController loginController =
                new LoginController();

        AbstractView dummyView = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(
                    Map<String, Object> model,
                    HttpServletRequest request,
                    HttpServletResponse response
            ) {
                // Render işlemi gerekmiyor.
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(loginController)
                .setSingleView(dummyView)
                .build();
    }

    @Test
    void shouldShowLoginPage() throws Exception {

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }
}

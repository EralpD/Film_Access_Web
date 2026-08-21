package com.example.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.user.service.UserRegistrationService;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SecurityCsrfTest {


    @Container
    static PostgreSQLContainer<?> postgress = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17")
    )
    .withDatabaseName("film_db")
    .withUsername("postgres")
    .withPassword("postgres");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    // ────────────────────────────────────────────────────────

    @Test
    void shouldRejectRegisterPostWithoutCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/register")
                    .param("displayName", "Eralp2")
                    .param("email", "eralp@example.com")
                    .param("password", "StrongPassword123!")
                    .param("passwordConfirmation", "StrongPassword123!")
            )
            .andDo(print())
            .andExpect(status().isForbidden())
            .andReturn();

        System.out.println();
        System.out.println("========================================");
        System.out.println("CSRF SECURITY TEST");
        System.out.println("POST /register without CSRF token");
        System.out.println("HTTP STATUS: " + result.getResponse().getStatus());
        System.out.println("ACCESS DENIED AS EXPECTED");
        System.out.println("========================================");
    }
}

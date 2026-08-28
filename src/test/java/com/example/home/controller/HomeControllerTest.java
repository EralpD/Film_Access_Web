package com.example.home.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;
import com.example.home.recommendation.RecommendationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendationService recommendationService;

    @Test
    @WithMockUser
    void homeDisplaysSharedFilmArchiveIconLinkingToRoot() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(xpath("//header/a[@class='site-brand' and @href='/']/svg").exists())
                .andExpect(xpath("//header/a[@class='site-brand']/span").string("\n        Film Archive\n    "));
    }

    @Test
    void rootRedirectsToHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }
}

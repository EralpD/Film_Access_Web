package com.example.omdb.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.film.service.FilmCatalogDiscoveryService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.verify;

import com.example.config.SecurityConfig;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.omdb.service.OmdbService;

@WebMvcTest(FilmSearchController.class)
@Import(SecurityConfig.class)
class FilmSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OmdbFilmDetailService filmDetailService;

    @MockitoBean
    private OmdbService omdbService;

    @MockitoBean
        private FilmCatalogDiscoveryService
        filmCatalogDiscoveryService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldShowFilmDetailForAuthenticatedUser()
            throws Exception {

        FilmDetail film = new FilmDetail(
                "tt1375666",
                "Inception",
                "2010",
                2010,
                null,
                148,
                "Action, Adventure, Sci-Fi",
                "Christopher Nolan",
                "Leonardo DiCaprio",
                "A thief enters dreams.",
                null,
                new BigDecimal("8.8"),
                "movie"
        );

        when(
                filmDetailService.getFilmDetail("tt1375666")
        ).thenReturn(film);

        mockMvc.perform(
                        get("/search/tt1375666")
                )
                .andExpect(status().isOk())
                .andExpect(
                        view().name("film-detail")
                )
                .andExpect(
                        model().attributeExists("film")
                )
                .andExpect(
                        model().attribute(
                                "film",
                                film
                        )
                );
        verify(
                filmCatalogDiscoveryService
                ).catalogViewedFilm(film);
    }

    @Test
    void shouldRequireAuthenticationForFilmDetail()
            throws Exception {

        mockMvc.perform(
                        get("/search/tt1375666")
                )
                .andExpect(
                        status().is3xxRedirection()
                );
    }


}

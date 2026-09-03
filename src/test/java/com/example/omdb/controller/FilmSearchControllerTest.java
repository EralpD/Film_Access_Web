package com.example.omdb.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.film.service.FilmCatalogDiscoveryService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.verify;
import com.example.buddy.service.BuddyRecommendationService;
import com.example.buddy.BuddyRecommendationResponse;
import com.example.buddy.web.MioCatalogBootstrapConfiguration;
import com.example.film.FilmCatalogBootstrapRunner;
import com.example.config.SecurityConfig;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.exception.FilmNotFoundException;
import com.example.omdb.exception.InvalidImdbIdException;
import com.example.omdb.exception.OmdbInvalidResponseException;
import com.example.omdb.service.OmdbService;
import com.example.discovery.DiscoverySearchService;
import com.example.discovery.DiscoverySearchResult;
import com.example.discovery.SearchScope;

@WebMvcTest(FilmSearchController.class)
@Import({SecurityConfig.class, MioCatalogBootstrapConfiguration.class})
class FilmSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FilmCatalogBootstrapRunner bootstrapRunner;

    @MockitoBean
    private OmdbService omdbService;

    @MockitoBean
    private DiscoverySearchService discoverySearchService;

    @MockitoBean
        private FilmCatalogDiscoveryService
        filmCatalogDiscoveryService;

        @MockitoBean
        private BuddyRecommendationService
                buddyRecommendationService;

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
                "Leonardo DiCaprio, Tom Hardy, N/A",
                "A thief enters dreams.",
                null,
                new BigDecimal("8.8"),
                "movie"
        );

        when(
                filmCatalogDiscoveryService.getFilmDetail("tt1375666")
        ).thenReturn(film);

        var response = mockMvc.perform(
                        get("/search/tt1375666")
                )
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/catalog?actor=Tom%20Hardy")))
                .andExpect(content().string(containsString("/catalog?director=Christopher%20Nolan")))
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
                ).andReturn().getResponse();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/qa"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/qa/detail.html"), response.getContentAsString());
        verify(filmCatalogDiscoveryService)
                .getFilmDetail("tt1375666");
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

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldTriggerImportForEmptyMioResponse() throws Exception {
        BuddyRecommendationResponse response = new BuddyRecommendationResponse(
                "empty", "Film bulamadım.", "calm", List.of()
        );
        when(buddyRecommendationService.recommend("user@example.com", "Sakin film"))
                .thenReturn(response);

        mockMvc.perform(post("/search").with(csrf()).param("prompt", "Sakin film"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("buddyResponse", response));

        verify(bootstrapRunner).start();
    }

    @ParameterizedTest
    @ValueSource(strings = {"results", "waiting"})
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldNotTriggerImportForOtherMioStates(String state) throws Exception {
        when(buddyRecommendationService.recommend("user@example.com", "Sakin film"))
                .thenReturn(new BuddyRecommendationResponse(state, "Yanıt", "calm", List.of()));

        mockMvc.perform(post("/search").with(csrf()).param("prompt", "Sakin film"))
                .andExpect(status().isOk());

        verifyNoInteractions(bootstrapRunner);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldNotTriggerImportOnMioServiceFailure() throws Exception {
        when(buddyRecommendationService.recommend("user@example.com", "Sakin film"))
                .thenThrow(new IllegalStateException("Service unavailable"));

        mockMvc.perform(post("/search").with(csrf()).param("prompt", "Sakin film"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("buddyError",
                        "Mio cannot search for films right now. Please try again shortly."));

        verifyNoInteractions(bootstrapRunner);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldNotTriggerImportForInvalidPromptOrNormalSearch() throws Exception {
        mockMvc.perform(post("/search").with(csrf()).param("prompt", " "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("buddyError",
                        "Please describe what you would like to watch."));
        when(discoverySearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(DiscoverySearchResult.empty(SearchScope.OMDB));
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What would you like to watch tonight?")))
                .andExpect(content().string(containsString("Mio, find a film")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("OMDb results"))));

        verifyNoInteractions(bootstrapRunner, buddyRecommendationService);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void catalogWithoutSearchShowsPersonalizedBrowsePageWithPagination() throws Exception {
        var film = new com.example.archive.response.ArchiveFilmResponse(
                10L, "tt0000010", "Catalog Film", "2020", "movie", "Drama", "N/A", null,
                "Recommended for you");
        var page = new org.springframework.data.domain.PageImpl<>(
                List.of(film), org.springframework.data.domain.PageRequest.of(0, 20), 21);
        var catalog = new com.example.archive.response.ArchiveSearchResult(page, 21, 0, false);
        when(discoverySearchService.browseCatalog(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DiscoverySearchResult(
                        SearchScope.CATALOG, true, catalog, List.of(), 1, 0, null, null));

        mockMvc.perform(get("/catalog"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(content().string(containsString("Catalog Film")))
                .andExpect(content().string(containsString("Recommended for you")))
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("catalogPage=1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("OMDb results"))));

        verify(discoverySearchService).browseCatalog(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser
    void catalogRouteRendersCatalogAndPreservesLegacyFilters() throws Exception {
        var catalog = new com.example.archive.response.ArchiveSearchResult(
                org.springframework.data.domain.Page.empty(), 0, 0, false);
        when(discoverySearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DiscoverySearchResult(
                        SearchScope.CATALOG, true, catalog, List.of(), 1, 0, null, null));

        mockMvc.perform(get("/catalog")
                        .param("actor", "Tom Hardy")
                        .param("sort", "year,asc")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("searchPath", "/catalog"))
                .andExpect(model().attribute("providerName", "Catalog"))
                .andExpect(content().string(containsString("Catalog results")))
                .andExpect(content().string(containsString("What would you like to watch tonight?")))
                .andExpect(content().string(containsString("Mio, find a film")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("OMDb results"))));

        var captor = org.mockito.ArgumentCaptor.forClass(com.example.discovery.DiscoverySearchRequest.class);
        verify(discoverySearchService).search(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().resolvedScope()).isEqualTo(SearchScope.CATALOG);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getActor()).isEqualTo("Tom Hardy");
    }

    @Test
    @WithMockUser
    void searchRouteRendersOnlyOmdbResultsEvenIfScopeIsManipulated() throws Exception {
        var remote = new com.example.discovery.DiscoveryOmdbFilm(
                "tt2", "Contact", "1997", "movie", "N/A", false);
        when(discoverySearchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(
                new DiscoverySearchResult(SearchScope.OMDB, true, null, List.of(remote), 1, 1, null, null));

        mockMvc.perform(get("/search").param("query", "space").param("scope", "catalog"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("searchPath", "/search"))
                .andExpect(content().string(containsString("OMDb results")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Catalog results"))));

        var captor = org.mockito.ArgumentCaptor.forClass(com.example.discovery.DiscoverySearchRequest.class);
        verify(discoverySearchService).search(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().resolvedScope()).isEqualTo(SearchScope.OMDB);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldKeepCsrfProtectionForMio() throws Exception {
        mockMvc.perform(post("/search").param("prompt", "Sakin film"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bootstrapRunner, buddyRecommendationService);
    }

    @Test
    @WithMockUser
    void shouldShowRequestLengthValidationInEnglish() throws Exception {
        mockMvc.perform(post("/search").with(csrf()).param("prompt", "x".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("buddyError",
                        "Your request must be no longer than 500 characters."));

        verifyNoInteractions(bootstrapRunner, buddyRecommendationService);
    }

    @ParameterizedTest
    @CsvSource({
            "invalid, Invalid IMDb ID.",
            "missing, Film not found.",
            "service, The film service returned an unexpected response."
    })
    @WithMockUser
    void shouldRenderFilmErrorsInEnglish(String errorType, String message) throws Exception {
        RuntimeException error = switch (errorType) {
            case "invalid" -> new InvalidImdbIdException("internal error");
            case "missing" -> new FilmNotFoundException("internal error");
            default -> new OmdbInvalidResponseException("internal error");
        };
        when(filmCatalogDiscoveryService.getFilmDetail("tt1375666")).thenThrow(error);

        mockMvc.perform(get("/search/tt1375666"))
                .andExpect(view().name("error/film-error"))
                .andExpect(model().attribute("errorMessage", message))
                .andExpect(content().string(containsString(message)));
    }


}

package com.example.archive;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.archive.controller.ArchiveController;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveSearchResult;
import com.example.archive.service.ArchiveService;
import com.example.config.SecurityConfig;

@WebMvcTest(ArchiveController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "user@example.com")
class ArchiveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArchiveService service;

    @Test
    void autoSurvivesInitialPageAndFirstQueryUsesRelevance() throws Exception {
        when(service.searchArchiveWithStatus(eq("user@example.com"), any()))
                .thenReturn(new ArchiveSearchResult(Page.empty(), 0, 0, false));
        mvc.perform(get("/archive")).andExpect(status().isOk())
                .andExpect(model().attribute("filters",
                        org.hamcrest.Matchers.hasProperty("sort", org.hamcrest.Matchers.is("auto"))))
                .andExpect(content().string(not(containsString("id=\"result-sort\""))));
        mvc.perform(get("/archive").param("query", "Interstelar").param("sort", "auto"))
                .andExpect(status().isOk()).andExpect(model().attribute("activeSort", "relevance,desc"));
        var capture = org.mockito.ArgumentCaptor.forClass(ArchiveSearchRequest.class);
        verify(service, times(2)).searchArchiveWithStatus(eq("user@example.com"), capture.capture());
        org.assertj.core.api.Assertions.assertThat(capture.getValue().resolveSortOption())
                .isEqualTo(ArchiveSortOption.RELEVANCE_DESC);
    }

    @Test
    void invalidYearRangeShowsValidationWithoutSearching() throws Exception {
        mvc.perform(get("/archive").param("query", "space").param("yearFrom", "2020").param("yearTo", "2000"))
                .andExpect(status().isOk()).andExpect(model().attribute("searchInvalid", true))
                .andExpect(content().string(containsString("Start year cannot be later than end year.")))
                .andExpect(content().string(not(containsString("<h2>No matching titles</h2>"))));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"yearFrom,1800", "yearTo,2200", "type,INVALID", "page,not-a-number"})
    void invalidInputsDoNotReachSearch(String field, String value) throws Exception {
        mvc.perform(get("/archive").param(field, value)).andExpect(status().isOk())
                .andExpect(model().attribute("searchInvalid", true));
        verifyNoInteractions(service);
    }

    @Test
    void longQueriesAreRejectedAndInputIsPreserved() throws Exception {
        mvc.perform(get("/archive").param("query", "a".repeat(501))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Search text must be 500 characters or fewer.")));
        verifyNoInteractions(service);
    }

    @Test
    void partialCoverageAndApiFailureAreVisible() throws Exception {
        when(service.searchArchiveWithStatus(any(), any()))
                .thenReturn(new ArchiveSearchResult(Page.empty(), 3, 2, true));
        mvc.perform(get("/archive").param("query", "space")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Semantic search is temporarily unavailable.")))
                .andExpect(content().string(containsString("Semantic results may be incomplete.")));
    }

    @Test
    void explicitSortIsPreserved() throws Exception {
        when(service.searchArchiveWithStatus(any(), any()))
                .thenReturn(new ArchiveSearchResult(Page.empty(), 0, 0, false));
        mvc.perform(get("/archive").param("query", "space").param("sort", "year,asc"))
                .andExpect(status().isOk()).andExpect(model().attribute("activeSort", "year,asc"));
    }

    @Test
    void resultSortAppearsOnlyAfterSearchFindsTitlesAndKeepsTheSelectedValue() throws Exception {
        var film = new com.example.archive.response.ArchiveFilmResponse(
                7L, "tt1375666", "Inception", "2010", "movie", "Action, Sci-Fi",
                null, java.time.OffsetDateTime.now(), "Title match");
        var page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(film));
        when(service.searchArchiveWithStatus(eq("user@example.com"), any()))
                .thenReturn(new ArchiveSearchResult(page, 1, 0, false));

        mvc.perform(get("/archive").param("query", "dream").param("sort", "year,asc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"result-sort\"")))
                .andExpect(content().string(containsString("selected=\"selected\">Oldest release")))
                .andExpect(content().string(containsString("<option value=\"auto\">")))
                .andExpect(content().string(not(containsString("Auto ("))))
                .andExpect(content().string(not(containsString("id=\"genre\""))))
                .andExpect(content().string(not(containsString("id=\"actor\""))))
                .andExpect(content().string(not(containsString("id=\"director\""))))
                .andExpect(content().string(not(containsString("Always include meaning matches"))));
    }

    @Test
    void resultSortStaysHiddenWhenSearchReturnsNoTitles() throws Exception {
        when(service.searchArchiveWithStatus(eq("user@example.com"), any()))
                .thenReturn(new ArchiveSearchResult(Page.empty(), 0, 0, false));

        mvc.perform(get("/archive").param("query", "missing"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"result-sort\""))));
    }

    @Test
    void resultSortIsVisibleForAnUnfilteredCollectionWithTitles() throws Exception {
        var film = new com.example.archive.response.ArchiveFilmResponse(
                8L, "tt0133093", "The Matrix", "1999", "movie", "Action, Sci-Fi",
                null, java.time.OffsetDateTime.now(), null);
        var page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(film));
        when(service.searchArchiveWithStatus(eq("user@example.com"), any()))
                .thenReturn(new ArchiveSearchResult(page, 1, 0, false));

        mvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"result-sort\"")))
                .andExpect(content().string(not(containsString(
                        "Showing direct title, people or genre matches without an AI request."))));
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void anonymousUsersCannotSearchArchives() throws Exception {
        mvc.perform(get("/archive")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }

    @Test
    void longPersonFiltersAreRejectedWithoutSearching() throws Exception {
        mvc.perform(get("/archive").param("actor", "a".repeat(201)))
                .andExpect(status().isOk()).andExpect(model().attribute("searchInvalid", true));
        verifyNoInteractions(service);
    }

    @Test
    void spellingBudgetAndDirectModeAreExplained() throws Exception {
        when(service.searchArchiveWithStatus(any(), any())).thenReturn(
                new ArchiveSearchResult(Page.empty(), 500, 0, false, true, "spelling"));
        mvc.perform(get("/archive").param("query", "Tennet"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("close title correction without an AI request")))
                .andExpect(content().string(containsString("not every possible spelling match")));
    }
}

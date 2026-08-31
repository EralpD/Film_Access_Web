package com.example.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.archive.response.ArchiveSearchResult;
import com.example.archive.service.ArchiveService;
import com.example.film.FilmRepository;
import com.example.omdb.dto.OmdbSearchItem;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.service.OmdbService;

@ExtendWith(MockitoExtension.class)
class DiscoverySearchServiceTest {
    @Mock ArchiveService archiveService;
    @Mock OmdbService omdbService;
    @Mock FilmRepository filmRepository;
    DiscoverySearchService service;

    @BeforeEach
    void setUp() {
        service = new DiscoverySearchService(archiveService, omdbService, filmRepository);
    }

    @Test
    void catalogScopeDoesNotCallOmdb() {
        DiscoverySearchRequest request = request("catalog");
        when(archiveService.searchCatalogWithStatus(any())).thenReturn(emptyCatalog());

        DiscoverySearchResult result = service.search(request);

        assertThat(result.catalogAttempted()).isTrue();
        assertThat(result.omdbAttempted()).isFalse();
        verifyNoInteractions(omdbService, filmRepository);
    }

    @Test
    void omdbScopeDoesNotRunCatalogSearch() {
        DiscoverySearchRequest request = request("omdb");
        when(omdbService.searchMovies(any(), any(), any(), any(Integer.class))).thenReturn(omdb(item("tt2", "Arrival")));
        when(filmRepository.findExistingImdbIds(any())).thenReturn(Set.of());

        DiscoverySearchResult result = service.search(request);

        assertThat(result.omdbFilms()).extracting(DiscoveryOmdbFilm::imdbId).containsExactly("tt2");
        verify(archiveService, never()).searchCatalogWithStatus(any());
    }

    @Test
    void omdbScopeDeduplicatesResultsAndUsesOneBulkCatalogLookup() {
        DiscoverySearchRequest request = request("omdb");
        when(omdbService.searchMovies(any(), any(), any(), any(Integer.class)))
                .thenReturn(omdb(item("tt1", "Inception"), item("tt2", "Arrival"), item("tt2", "Arrival")));
        when(filmRepository.findExistingImdbIds(any())).thenReturn(Set.of("tt1", "tt2"));

        DiscoverySearchResult result = service.search(request);

        assertThat(result.omdbFilms()).hasSize(2);
        assertThat(result.omdbFilms()).allMatch(DiscoveryOmdbFilm::inCatalog);
        verify(filmRepository).findExistingImdbIds(Set.of("tt1", "tt2"));
    }

    @Test
    void catalogFailureStaysInsideCatalogScope() {
        DiscoverySearchRequest request = request("catalog");
        when(archiveService.searchCatalogWithStatus(any())).thenThrow(new IllegalStateException("down"));

        DiscoverySearchResult result = service.search(request);

        assertThat(result.catalogError()).isNotBlank();
        assertThat(result.omdbAttempted()).isFalse();
        verifyNoInteractions(omdbService, filmRepository);
    }

    private DiscoverySearchRequest request(String scope) {
        DiscoverySearchRequest request = new DiscoverySearchRequest();
        request.setQuery("space");
        request.setScope(scope);
        return request;
    }

    private ArchiveSearchResult emptyCatalog() {
        return new ArchiveSearchResult(org.springframework.data.domain.Page.empty(), 0, 0, false);
    }

    private OmdbSearchResponse omdb(OmdbSearchItem... items) {
        OmdbSearchResponse response = new OmdbSearchResponse();
        response.setResponse("True");
        response.setTotalResults(Integer.toString(items.length));
        response.setSearch(List.of(items));
        return response;
    }

    private OmdbSearchItem item(String id, String title) {
        OmdbSearchItem item = new OmdbSearchItem();
        item.setImdbId(id);
        item.setTitle(title);
        item.setYear("2016");
        item.setType("movie");
        item.setPoster("N/A");
        return item;
    }
}

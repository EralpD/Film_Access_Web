package com.example.omdb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;
import com.example.omdb.service.OmdbService;
import com.example.film.service.FilmCatalogDiscoveryService;

@Controller
public class FilmSearchController {

    private final OmdbFilmDetailService filmDetailService;
    private final OmdbService filmSearchService;
    private final FilmCatalogDiscoveryService
        filmCatalogDiscoveryService;

    public FilmSearchController(
            OmdbFilmDetailService filmDetailService,
            OmdbService filmSearchService,
            FilmCatalogDiscoveryService filmCatalogDiscoveryService
    ) {

        this.filmDetailService =
                filmDetailService;

        this.filmSearchService =
                filmSearchService;

        this.filmCatalogDiscoveryService =
                filmCatalogDiscoveryService;    
    }

@GetMapping("/search")
public String searchFilms(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "1") int page,
        Model model
) {

    model.addAttribute("query", query);
    model.addAttribute("year", year);
    model.addAttribute("type", type);

    if (query == null || query.isBlank()) {
        model.addAttribute("results", java.util.Collections.emptyList());
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 0);

        return "search";
    }

    String normalizedQuery = query.trim();
    int normalizedPage = Math.max(page, 1);

    OmdbType omdbType = null;

    if (type != null && !type.isBlank()) {
        try {
            omdbType = OmdbType.valueOf(
                    type.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            omdbType = null;
        }
    }

    OmdbSearchResponse response =
            filmSearchService.searchMovies(
                    normalizedQuery,
                    year,
                    omdbType,
                    normalizedPage
            );

    if (response == null) {
        model.addAttribute("results", java.util.Collections.emptyList());
        model.addAttribute("currentPage", normalizedPage);
        model.addAttribute("totalPages", 0);
        model.addAttribute("query", normalizedQuery);
        model.addAttribute(
                "searchError",
                "Film service did not return a response."
        );

        return "search";
    }

    if (!response.isSuccessful()) {
        model.addAttribute("results", java.util.Collections.emptyList());
        model.addAttribute("currentPage", normalizedPage);
        model.addAttribute("totalPages", 0);
        model.addAttribute("query", normalizedQuery);
        model.addAttribute("searchError", response.getError());

        return "search";
    }

    model.addAttribute(
            "results",
            response.getSearch() != null
                    ? response.getSearch()
                    : java.util.Collections.emptyList()
    );

    model.addAttribute(
            "currentPage",
            normalizedPage
    );

    int totalPages = calculateTotalPages(
            response.getTotalResults()
    );

    model.addAttribute(
            "totalPages",
            totalPages
    );

    model.addAttribute(
            "query",
            normalizedQuery
    );

    return "search";
}

private int calculateTotalPages(String totalResults) {

    if (totalResults == null || totalResults.isBlank()) {
        return 0;
    }

    try {
        int resultCount = Integer.parseInt(totalResults);

        return (int) Math.ceil(
                resultCount / 10.0
        );

    } catch (NumberFormatException ex) {
        return 0;
    }
}

@GetMapping("/search/{imdbId}")
public String showFilmDetail(
        @PathVariable String imdbId,

        @RequestParam(
                defaultValue = "search"
        )
        String from,

        @RequestParam(
                required = false
        )
        String archiveQuery,

        @RequestParam(
                required = false
        )
        Integer archiveYearFrom,

        @RequestParam(
                required = false
        )
        Integer archiveYearTo,

        @RequestParam(
                required = false
        )
        String archiveType,

        @RequestParam(
                required = false
        )
        String archiveGenre,

        @RequestParam(
                required = false
        )
        String archiveSort,

        @RequestParam(
                required = false
        )
        Integer archiveSize,

        @RequestParam(
                required = false
        )
        Integer archivePage,

        Model model
) {

    FilmDetail film =
            filmDetailService.getFilmDetail(
                    imdbId
            );

    filmCatalogDiscoveryService.catalogViewedFilm(
        film
    );

    boolean fromArchive =
            "archive".equalsIgnoreCase(
                    from
            );

    boolean fromHome =
        "home".equalsIgnoreCase(
                from
        );

    model.addAttribute(
            "film",
            film
    );

    model.addAttribute(
            "fromArchive",
            fromArchive
    );

    model.addAttribute(
            "archiveQuery",
            archiveQuery
    );

    model.addAttribute(
            "archiveYearFrom",
            archiveYearFrom
    );

    model.addAttribute(
            "archiveYearTo",
            archiveYearTo
    );

    model.addAttribute(
            "archiveType",
            archiveType
    );

    model.addAttribute(
            "archiveGenre",
            archiveGenre
    );

    model.addAttribute(
            "archiveSort",
            archiveSort
    );

    model.addAttribute(
            "archiveSize",
            archiveSize
    );

    model.addAttribute(
            "archivePage",
            archivePage == null
                    ? 0
                    : archivePage
    );

    model.addAttribute(
        "fromHome",
        fromHome
    );

    return "film-detail";

}
}

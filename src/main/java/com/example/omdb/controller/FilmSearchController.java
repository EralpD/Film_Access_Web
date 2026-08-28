package com.example.omdb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;
import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbService;
import com.example.film.service.FilmCatalogDiscoveryService;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.buddy.BuddyRecommendationRequest;
import com.example.buddy.BuddyRecommendationResponse;
import com.example.buddy.service.BuddyRecommendationService;
import com.example.buddy.web.MioCatalogBootstrapFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
public class FilmSearchController {

    private final OmdbService filmSearchService;
    private final FilmCatalogDiscoveryService
        filmCatalogDiscoveryService;

    private static final Logger log =
                LoggerFactory.getLogger(
                FilmSearchController.class
                );

    private final BuddyRecommendationService
                buddyRecommendationService;

        public FilmSearchController(
                OmdbService filmSearchService,
                FilmCatalogDiscoveryService
                        filmCatalogDiscoveryService,
                BuddyRecommendationService
                        buddyRecommendationService
        ) {
        this.filmSearchService =
                filmSearchService;

        this.filmCatalogDiscoveryService =
                filmCatalogDiscoveryService;

        this.buddyRecommendationService =
                buddyRecommendationService;
        }


@GetMapping("/search")
public String searchFilms(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "1") int page,
        Model model
) {

        prepareBuddyForm(model);

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

@PostMapping("/search")
public String searchWithBuddy(
        @Valid
        @ModelAttribute("buddyRequest")
        BuddyRecommendationRequest request,

        BindingResult bindingResult,
        Principal principal,
        Model model,
        HttpServletRequest httpRequest
) {
    prepareEmptySearchPage(model);

    model.addAttribute(
        "buddyMenuOpen",
        true
    );

    if (bindingResult.hasErrors()) {

        String message =
                bindingResult
                        .getAllErrors()
                        .getFirst()
                        .getDefaultMessage();

        model.addAttribute(
            "buddyError",
            message
        );

        return "search";
    }

    try {
        BuddyRecommendationResponse response =
                buddyRecommendationService
                        .recommend(
                            principal.getName(),
                            request.prompt().trim()
                        );

        model.addAttribute(
            "buddyResponse",
            response
        );

        if ("empty".equals(response.state())) {
            httpRequest.setAttribute(
                MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE,
                Boolean.TRUE
            );
        }

    } catch (RuntimeException exception) {

        /*
         * Kullanıcının prompt'unu loglama.
         */
        log.warn(
            "Mio recommendation request failed.",
            exception
        );

        model.addAttribute(
            "buddyError",
            "Mio cannot search for films right now. "
            + "Please try again shortly."
        );
    }

    return "search";
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

        @RequestParam(required = false) String archiveActor,
        @RequestParam(required = false) String archiveDirector,
        @RequestParam(defaultValue = "false") boolean archiveSemantic,

        Model model
) {

    FilmDetail film =
            filmCatalogDiscoveryService.getFilmDetail(
                    imdbId
            );

    boolean fromArchive =
            "archive".equalsIgnoreCase(
                    from
            );
    model.addAttribute("fromCatalog", "catalog".equalsIgnoreCase(from));
    model.addAttribute("archiveActor", archiveActor);
    model.addAttribute("archiveDirector", archiveDirector);
    model.addAttribute("archiveSemantic", archiveSemantic);

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

private void prepareBuddyForm(Model model) {

    if (!model.containsAttribute("buddyRequest")) {

        model.addAttribute(
            "buddyRequest",
            new BuddyRecommendationRequest("")
        );
    }

    if (!model.containsAttribute("buddyMenuOpen")) {

        model.addAttribute(
            "buddyMenuOpen",
            false
        );
    }
}

private void prepareEmptySearchPage(Model model) {

    model.addAttribute(
        "results",
        java.util.Collections.emptyList()
    );

    model.addAttribute("query", null);
    model.addAttribute("year", null);
    model.addAttribute("type", null);
    model.addAttribute("currentPage", 1);
    model.addAttribute("totalPages", 0);
}
}

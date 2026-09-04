package com.example.omdb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.omdb.model.FilmDetail;
import com.example.film.service.FilmCatalogDiscoveryService;
import com.example.archive.option.ArchiveSortOption;
import com.example.discovery.DiscoverySearchRequest;
import com.example.discovery.DiscoverySearchResult;
import com.example.discovery.DiscoverySearchService;
import com.example.discovery.SearchScope;

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

    private final DiscoverySearchService discoverySearchService;
    private final FilmCatalogDiscoveryService
        filmCatalogDiscoveryService;

    private static final Logger log =
                LoggerFactory.getLogger(
                FilmSearchController.class
                );

    private final BuddyRecommendationService
                buddyRecommendationService;

        public FilmSearchController(
                FilmCatalogDiscoveryService
                        filmCatalogDiscoveryService,
                BuddyRecommendationService
                        buddyRecommendationService,
                DiscoverySearchService discoverySearchService
        ) {
        this.filmCatalogDiscoveryService =
                filmCatalogDiscoveryService;

        this.buddyRecommendationService =
                buddyRecommendationService;
        this.discoverySearchService = discoverySearchService;
        }


@GetMapping({"/search", "/catalog"})
public String searchFilms(
        @Valid @ModelAttribute("searchRequest") DiscoverySearchRequest request,
        BindingResult bindingResult,
        Principal principal,
        HttpServletRequest httpRequest,
        Model model
) {
    prepareBuddyForm(model);
    boolean catalogRoute = requestPath(httpRequest).equals("/catalog");
    request.setScope(catalogRoute ? "catalog" : "omdb");
    DiscoverySearchResult result;
    if (bindingResult.hasErrors()) {
        result = DiscoverySearchResult.empty(request.resolvedScope());
    } else if (catalogRoute && !request.hasSearchCriteria()) {
        result = discoverySearchService.browseCatalog(principal.getName(), request);
    } else {
        result = discoverySearchService.search(request);
    }
    model.addAttribute("discovery", result);
    model.addAttribute("sortOptions", ArchiveSortOption.availableFor(request.isSemantic()));
    model.addAttribute("searchInvalid", bindingResult.hasErrors());
    model.addAttribute("searchPath", catalogRoute ? "/catalog" : "/search");
    model.addAttribute("providerName", catalogRoute ? "Catalog" : "OMDb");
    return "search";
}

private String requestPath(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
}

private void prepareDiscoveryPage(Model model) {
    if (!model.containsAttribute("searchRequest")) {
        model.addAttribute("searchRequest", new DiscoverySearchRequest());
    }
    model.addAttribute("discovery", DiscoverySearchResult.empty(SearchScope.OMDB));
    model.addAttribute("sortOptions", ArchiveSortOption.availableFor(false));
    model.addAttribute("searchInvalid", false);
    model.addAttribute("searchPath", "/search");
    model.addAttribute("providerName", "OMDb");
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

        @RequestParam(required = false) String discoverQuery,
        @RequestParam(required = false) Integer discoverYear,
        @RequestParam(required = false) String discoverType,
        @RequestParam(defaultValue = "all") String discoverScope,
        @RequestParam(defaultValue = "auto") String discoverCatalogSort,
        @RequestParam(defaultValue = "0") Integer discoverCatalogPage,
        @RequestParam(defaultValue = "1") Integer discoverOmdbPage,

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
    model.addAttribute("fromDiscover", "discover".equalsIgnoreCase(from));
    model.addAttribute("archiveActor", archiveActor);
    model.addAttribute("archiveDirector", archiveDirector);
    model.addAttribute("archiveSemantic", archiveSemantic);
    model.addAttribute("discoverQuery", discoverQuery);
    model.addAttribute("discoverYear", discoverYear);
    model.addAttribute("discoverType", discoverType);
    model.addAttribute("discoverScope", discoverScope);
    model.addAttribute("discoverCatalogSort", discoverCatalogSort);
    model.addAttribute("discoverCatalogPage", discoverCatalogPage);
    model.addAttribute("discoverOmdbPage", discoverOmdbPage);

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
    prepareDiscoveryPage(model);
}
}

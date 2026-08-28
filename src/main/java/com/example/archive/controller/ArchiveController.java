package com.example.archive.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import com.example.archive.ArchiveSearchRequest;
import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveSearchResult;
import com.example.archive.service.ArchiveService;

@Controller
public class ArchiveController {
    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) { this.archiveService = archiveService; }

    @PostMapping("/archive")
    public String addFilmToArchive(@RequestParam String imdbId,
            @RequestParam(defaultValue = "detail") String returnTo,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            archiveService.addFilmToArchive(authentication.getName(), imdbId);
            redirectAttributes.addFlashAttribute("archiveSuccess", "Film added to your archive.");
        } catch (FilmAlreadyInArchiveException ex) {
            redirectAttributes.addFlashAttribute("archiveInfo", "This film is already in your archive.");
        }
        return "home".equalsIgnoreCase(returnTo) ? "redirect:/home" : "redirect:/search/" + imdbId;
    }

    @GetMapping({"/archive", "/catalog"})
    public String archive(@Valid @ModelAttribute("filters") ArchiveSearchRequest request,
            BindingResult binding, Principal principal, Model model, jakarta.servlet.http.HttpServletRequest http) {
        boolean catalog = "/catalog".equals(http.getRequestURI().substring(http.getContextPath().length()));
        request.setPage(request.normalizedPage());
        request.setSize(request.normalizedSize());
        ArchiveSortOption activeSort = request.resolveSortOption();
        // Preserve Auto across requests; do not turn the initial default into an explicit user choice.
        if (request.getSort() == null || request.getSort().isBlank()
                || "auto".equalsIgnoreCase(request.getSort().trim())) {
            request.setSort("auto");
        } else {
            request.setSort(activeSort.getRequestValue());
        }

        ArchiveSearchResult result = binding.hasErrors()
                ? new ArchiveSearchResult(Page.empty(PageRequest.of(0, request.normalizedSize())), 0, 0, false)
                : catalog ? archiveService.searchCatalogWithStatus(request)
                : archiveService.searchArchiveWithStatus(principal.getName(), request);
        var page = result.page();
        model.addAttribute("archivePage", page);
        model.addAttribute("catalog", catalog);
        model.addAttribute("searchPath", catalog ? "/catalog" : "/archive");
        model.addAttribute("films", page.getContent());
        model.addAttribute("archiveFilms", page.getContent());
        model.addAttribute("sortOptions", ArchiveSortOption.availableFor(request.hasSemanticQuery()));
        model.addAttribute("activeSort", activeSort.getRequestValue());
        model.addAttribute("pageNumbers", createPageWindow(page));
        model.addAttribute("hasFilters", request.hasAnyFilter());
        model.addAttribute("searchStatus", result);
        model.addAttribute("searchInvalid", binding.hasErrors());
        model.addAttribute("searchErrors", binding.getAllErrors().stream()
                .map(error -> error instanceof FieldError field && field.isBindingFailure()
                        ? "Please check the year, type and pagination values."
                        : error.getDefaultMessage()).distinct().toList());
        return "archive";
    }

    private List<Integer> createPageWindow(Page<?> page) {
        if (page.getTotalPages() == 0) return List.of();
        int start = Math.max(0, page.getNumber() - 2);
        int end = Math.min(page.getTotalPages() - 1, start + 4);
        return IntStream.rangeClosed(Math.max(0, end - 4), end).boxed().toList();
    }

    @PostMapping("/archive/{userFilmId}/delete")
    public String removeFromArchive(@PathVariable Long userFilmId, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        archiveService.deleteArchive(authentication.getName(), userFilmId);
        redirectAttributes.addFlashAttribute("successMessage", "Film removed from your archive.");
        return "redirect:/archive";
    }
}

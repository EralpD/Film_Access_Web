package com.example.archive.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.archive.ArchiveSearchRequest;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.archive.service.ArchiveService;


import java.security.Principal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.archive.ArchiveSearchRequest;
import com.example.archive.exception.FilmAlreadyInArchiveException;
import com.example.archive.option.ArchiveSortOption;
import com.example.archive.response.ArchiveFilmResponse;
import com.example.archive.service.ArchiveService;

import jakarta.validation.Valid;

import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.omdb.dto.OmdbType;


@Controller
@RequestMapping("/archive") // Make the route base as "/archive", useful for multiple uses
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(
            ArchiveService archiveService
    ) {
        this.archiveService = archiveService;
    }

    @PostMapping
    public String addFilmToArchive(
            @RequestParam String imdbId,
            @RequestParam(defaultValue = "detail")
            String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        try {

            archiveService.addFilmToArchive(
                    authentication.getName(),
                    imdbId
            );

            redirectAttributes.addFlashAttribute(
                    "archiveSuccess",
                    "Film arşivinize eklendi."
            );

        } catch (
            FilmAlreadyInArchiveException ex
        ) {

            redirectAttributes.addFlashAttribute(
                    "archiveInfo",
                    "Bu film zaten arşivinizde."
            );
        }

        if ("home".equalsIgnoreCase(returnTo)) {

        return "redirect:/home";
        }

        return "redirect:/search/" + imdbId;
    }

  @GetMapping
    public String archive(
            @ModelAttribute("filters")
            ArchiveSearchRequest request,
            Principal principal,
            Model model
    ) {

        /*
         * Request'i UI açısından da normalize ediyoruz.
         */
        request.setPage(
                request.normalizedPage()
        );

        request.setSize(
                request.normalizedSize()
        );


        ArchiveSortOption activeSort =
                request.resolveSortOption();


        request.setSort(
                activeSort.getRequestValue()
        );


        Page<ArchiveFilmResponse> archivePage =
                archiveService.searchArchive(
                        principal.getName(),
                        request
                );


        model.addAttribute(
                "archivePage",
                archivePage
        );


        /*
         * Mevcut archive.html "films" kullanıyorsa
         * template'i tamamen bozmayalım.
         */
        model.addAttribute(
                "films",
                archivePage.getContent()
        );


        model.addAttribute(
                "sortOptions",
                ArchiveSortOption.availableFor(
                        request.hasSemanticQuery()
                )
        );


        model.addAttribute(
                "activeSort",
                activeSort.getRequestValue()
        );


        model.addAttribute(
                "pageNumbers",
                createPageWindow(
                        archivePage
                )
        );

        model.addAttribute(
                "archiveFilms",
                archivePage.getContent()
        );

        model.addAttribute(
                "hasFilters",
                request.hasAnyFilter()
        );


        return "archive";
    }


    private List<Integer> createPageWindow(
            Page<?> page
    ) {

        if (page.getTotalPages() == 0) {
            return List.of();
        }


        int start =
                Math.max(
                        0,
                        page.getNumber() - 2
                );


        int end =
                Math.min(
                        page.getTotalPages() - 1,
                        start + 4
                );


        start =
                Math.max(
                        0,
                        end - 4
                );


        return IntStream
                .rangeClosed(
                        start,
                        end
                )
                .boxed()
                .toList();
    }



        @PostMapping("/{userFilmId}/delete")
        public String removeFromArchive(
                @PathVariable Long userFilmId,
                Authentication authentication,
                RedirectAttributes redirectAttributes
        ) {

            archiveService.deleteArchive(
                    authentication.getName(),
                    userFilmId
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Film arşivinizden çıkarıldı."
            );

            return "redirect:/archive";
        }


}

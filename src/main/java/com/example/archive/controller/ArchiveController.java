package com.example.archive.controller;

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

        return "redirect:/search/" + imdbId;
    }

    @GetMapping
        public String showArchive(
                @Valid
                @ModelAttribute("search")
                ArchiveSearchRequest search,

                BindingResult bindingResult,

                Authentication authentication,

                Model model
        ) {

        model.addAttribute(
                "types",
                OmdbType.values()
        );


        if (bindingResult.hasErrors()) {

                model.addAttribute(
                        "archiveFilms",
                        archiveService.getArchive(
                                authentication.getName()
                        )
                );

                model.addAttribute(
                        "hasFilters",
                        false
                );

                return "archive";
        }


        List<ArchiveFilmResponse> archiveFilms =
                archiveService.searchArchive(
                        authentication.getName(),
                        search
                );


        model.addAttribute(
                "archiveFilms",
                archiveFilms
        );

        model.addAttribute(
                "hasFilters",
                search.hasAnyFilter()
        );


        return "archive";
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

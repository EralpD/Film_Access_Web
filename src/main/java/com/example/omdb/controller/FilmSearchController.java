package com.example.omdb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.omdb.model.FilmDetail;
import com.example.omdb.service.OmdbFilmDetailService;

@Controller
public class FilmSearchController {

    private final OmdbFilmDetailService filmDetailService;

    public FilmSearchController(
            OmdbFilmDetailService filmDetailService
    ) {
        this.filmDetailService = filmDetailService;
    }

    @GetMapping("/search/{imdbId}")
    public String showFilmDetail(
            @PathVariable String imdbId,
            Model model
    ) {

        FilmDetail film =
                filmDetailService.getFilmDetail(imdbId);

        model.addAttribute("film", film);

        return "film-detail";
    }
}

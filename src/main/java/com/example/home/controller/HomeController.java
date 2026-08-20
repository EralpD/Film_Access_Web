package com.example.home.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.home.recommendation.RecommendationService;
import com.example.home.recommendation.RecommendedFilmResponse;

@Controller
public class HomeController {

    // RecommendationService has been implemented.
    private final RecommendationService
            recommendationService;


    public HomeController(
            RecommendationService recommendationService
    ) {

        this.recommendationService =
                recommendationService;
    }


    @GetMapping("/")
    public String redirectToHome() {

        return "redirect:/home";
    }


    @GetMapping("/home")
    public String showHome(
            Principal principal,
            Model model
    ) {

        List<RecommendedFilmResponse> recommendations =
                recommendationService.recommendFor(
                        principal.getName()
                );

        model.addAttribute(
                "recommendedFilms",
                recommendations
        );

        return "home";
    }
}
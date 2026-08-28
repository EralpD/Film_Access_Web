package com.example.common.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.example.omdb.exception.FilmNotFoundException;
import com.example.omdb.exception.InvalidImdbIdException;
import com.example.omdb.exception.OmdbInvalidResponseException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidImdbIdException.class)
    public String handleInvalidImdbId(
            InvalidImdbIdException exception,
            Model model
    ) {

        model.addAttribute(
                "errorMessage",
                "Invalid IMDb ID."
        );

        return "error/film-error";
    }

    @ExceptionHandler(FilmNotFoundException.class)
    public String handleFilmNotFound(
            FilmNotFoundException exception,
            Model model
    ) {

        model.addAttribute(
                "errorMessage",
                "Film not found."
        );

        return "error/film-error";
    }

    @ExceptionHandler(OmdbInvalidResponseException.class)
    public String handleInvalidOmdbResponse(
            OmdbInvalidResponseException exception,
            Model model
    ) {

        model.addAttribute(
                "errorMessage",
                "The film service returned an unexpected response."
        );

        return "error/film-error";
    }
}

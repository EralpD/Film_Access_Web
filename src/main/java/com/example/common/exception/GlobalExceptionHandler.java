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
                "Geçersiz IMDb kimliği."
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
                "Film bulunamadı."
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
                "Film servisi beklenmeyen bir cevap verdi."
        );

        return "error/film-error";
    }
}

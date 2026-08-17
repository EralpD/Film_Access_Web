package com.example.archive.exception;

public class FilmAlreadyInArchiveException
        extends RuntimeException {

    public FilmAlreadyInArchiveException(
            String imdbId
    ) {
        super(
            "Film is already in the user's archive: "
            + imdbId
        );
    }
}

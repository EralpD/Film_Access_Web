package com.example.omdb.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.omdb.client.OmdbClient;
import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.exception.FilmNotFoundException;
import com.example.omdb.exception.InvalidImdbIdException;
import com.example.omdb.exception.OmdbInvalidResponseException;
import com.example.omdb.mapper.OmdbFilmMapper;
import com.example.omdb.model.FilmDetail;

@Service
public class OmdbFilmDetailService {

    private static final Pattern IMDB_ID_PATTERN =
            Pattern.compile("^tt\\d+$");

    private final OmdbClient omdbClient;
    private final OmdbFilmMapper omdbFilmMapper;

    public OmdbFilmDetailService(
            OmdbClient omdbClient,
            OmdbFilmMapper omdbFilmMapper
    ) {
        this.omdbClient = omdbClient;
        this.omdbFilmMapper = omdbFilmMapper;
    }

    public FilmDetail getFilmDetail(String imdbId) {

        validateImdbId(imdbId);

        OmdbDetailResponse response =
            omdbClient.getDetails(imdbId);

        validateOmdbResponse(response);

        return omdbFilmMapper.toFilmDetail(response);
    }

    private void validateImdbId(String imdbId) {

        if (imdbId == null
                || imdbId.isBlank()
                || !IMDB_ID_PATTERN.matcher(imdbId).matches()) {

            throw new InvalidImdbIdException(
                    "Invalid IMDb ID: " + imdbId
            );
        }
    }

    private void validateOmdbResponse(
            OmdbDetailResponse response
    ) {

        if (response == null) {
            throw new OmdbInvalidResponseException(
                    "OMDb returned an empty response."
            );
        }

        if ("False".equalsIgnoreCase(response.getResponse())) {

            throw new FilmNotFoundException(
                    response.getError() != null
                            ? response.getError()
                            : "Film could not be found."
            );
        }
    }
}

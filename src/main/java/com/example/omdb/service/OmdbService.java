package com.example.omdb.service;

import org.springframework.stereotype.Service;

import com.example.omdb.client.OmdbClient;
import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;


@Service
public class OmdbService {

    private final OmdbClient omdbClient;


    public OmdbService(
        OmdbClient omdbClient
    ) {
        this.omdbClient = omdbClient;
    }


    public OmdbSearchResponse searchMovies(
        String title,
        Integer year,
        OmdbType type,
        int page
    ) {

        return omdbClient.search(
            title,
            year,
            type,
            page
        );
    }

    public OmdbDetailResponse getMovieDetails(String imdbId) {
        return omdbClient.getDetails(imdbId);
    }
}


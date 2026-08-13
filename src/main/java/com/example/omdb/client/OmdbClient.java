package com.example.omdb.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.omdb.config.OmdbProperties;
import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;


@Component
public class OmdbClient {

    private final RestClient restClient;

    private final OmdbProperties properties;


    public OmdbClient(
        RestClient restClient,
        OmdbProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }


public OmdbSearchResponse search(
    String title,
    Integer year,
    OmdbType type,
    int page
) {

    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException(
            "Search title cannot be blank."
        );
    }

    if (page < 1 || page > 100) {
        throw new IllegalArgumentException(
            "Page must be between 1 and 100."
        );
    }

    return restClient
        .get()
        .uri(uriBuilder -> {

            uriBuilder
                .queryParam(
                    "apikey",
                    properties.getApiKey()
                )
                .queryParam(
                    "s",
                    title.trim()
                )
                .queryParam(
                    "page",
                    page
                );


            if (year != null) {
                uriBuilder.queryParam(
                    "y",
                    year
                );
            }


            if (type != null) {
                uriBuilder.queryParam(
                    "type",
                    type.getApiValue()
                );
            }


            return uriBuilder.build();
        })
        .retrieve()
        .body(OmdbSearchResponse.class);
    }

    private void validateImdbId(String imdbId) { // Validate IMDb ID, this is in client because checking before invalid request send 

    if (
        imdbId == null ||
        !imdbId.matches("^tt\\d+$")
    ) {
        throw new IllegalArgumentException(
            "Invalid IMDb ID."
        );
    }
    }

    public OmdbDetailResponse getDetails( // Creating a request
    String imdbId
) {

    validateImdbId(imdbId);

    return restClient
        .get()
        .uri(uriBuilder -> uriBuilder
            .queryParam(
                "apikey",
                properties.getApiKey()
            )
            .queryParam(
                "i",
                imdbId
            )
            .queryParam(
                "plot",
                "full"
            )
            .build()
        )
        .retrieve()
        .body(OmdbDetailResponse.class);
    }

}
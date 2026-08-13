package com.example.omdb.service;

import com.example.omdb.client.OmdbClient;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;
import com.example.omdb.dto.OmdbDetailResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class OmdbServiceTest {

    @Mock
    private OmdbClient omdbClient;


    private OmdbService omdbService;


    @BeforeEach
    void setUp() {

        omdbService =
            new OmdbService(
                omdbClient
            );
    }


    @Test
    void shouldDelegateSearchToOmdbClient() {

        OmdbSearchResponse expectedResponse =
            new OmdbSearchResponse();


        when(
            omdbClient.search(
                "Batman",
                2005,
                OmdbType.MOVIE,
                1
            )
        ).thenReturn(
            expectedResponse
        );


        OmdbSearchResponse actualResponse =
            omdbService.searchMovies(
                "Batman",
                2005,
                OmdbType.MOVIE,
                1
            );


        assertSame(
            expectedResponse,
            actualResponse
        );


        verify(
            omdbClient
        ).search(
            "Batman",
            2005,
            OmdbType.MOVIE,
            1
        );
    }

    @Test
    void shouldDelegateDetailRequestToOmdbClient() {

        OmdbDetailResponse expectedResponse =
            new OmdbDetailResponse();


        when(
            omdbClient.getDetails(
                "tt0372784"
            )
        ).thenReturn(
            expectedResponse
        );


        OmdbDetailResponse actualResponse =
            omdbService.getMovieDetails(
                "tt0372784"
            );


        assertSame(
            expectedResponse,
            actualResponse
        );


        verify(
            omdbClient
        ).getDetails(
            "tt0372784"
        );
    }

}

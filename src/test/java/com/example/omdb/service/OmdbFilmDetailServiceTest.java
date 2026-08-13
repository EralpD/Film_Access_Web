package com.example.omdb.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.omdb.client.OmdbClient;
import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.exception.FilmNotFoundException;
import com.example.omdb.exception.InvalidImdbIdException;
import com.example.omdb.mapper.OmdbFilmMapper;
import com.example.omdb.model.FilmDetail;

class OmdbFilmDetailServiceTest {

    private OmdbClient omdbClient;
    private OmdbFilmMapper mapper;

    private OmdbFilmDetailService service;

    @BeforeEach
    void setUp() {

        omdbClient = mock(OmdbClient.class);
        mapper = mock(OmdbFilmMapper.class);

        service = new OmdbFilmDetailService(
                omdbClient,
                mapper
        );
    }

    @Test
    void shouldReturnFilmDetailForValidImdbId() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        FilmDetail filmDetail =
                mock(FilmDetail.class);

        when(response.getResponse())
                .thenReturn("True");

        when(omdbClient.getDetails("tt1375666"))
                .thenReturn(response);

        when(mapper.toFilmDetail(response))
                .thenReturn(filmDetail);

        FilmDetail result =
                service.getFilmDetail("tt1375666");

        assertSame(filmDetail, result);

        verify(omdbClient)
                .getDetails("tt1375666");

        verify(mapper)
                .toFilmDetail(response);
    }

    @Test
    void shouldRejectInvalidImdbIdWithoutCallingOmdb() {

        assertThrows(
                InvalidImdbIdException.class,
                () -> service.getFilmDetail("abc")
        );

        verifyNoInteractions(omdbClient);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectBlankImdbIdWithoutCallingOmdb() {

        assertThrows(
                InvalidImdbIdException.class,
                () -> service.getFilmDetail(" ")
        );

        verifyNoInteractions(omdbClient);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectNullImdbIdWithoutCallingOmdb() {

        assertThrows(
                InvalidImdbIdException.class,
                () -> service.getFilmDetail(null)
        );

        verifyNoInteractions(omdbClient);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldThrowFilmNotFoundWhenOmdbResponseIsFalse() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getResponse())
                .thenReturn("False");

        when(response.getError())
                .thenReturn("Incorrect IMDb ID.");

        when(omdbClient.getDetails("tt99999999"))
                .thenReturn(response);

        assertThrows(
                FilmNotFoundException.class,
                () -> service.getFilmDetail("tt99999999")
        );

        verify(omdbClient)
                .getDetails("tt99999999");

        verifyNoInteractions(mapper);
    }
}

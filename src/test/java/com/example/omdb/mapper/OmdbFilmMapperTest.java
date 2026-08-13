package com.example.omdb.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.model.FilmDetail;

class OmdbFilmMapperTest {

    private OmdbFilmMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OmdbFilmMapper();
    }

    @Test
    void shouldMapValidOmdbFilmDetailResponse() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getImdbId()).thenReturn("tt1375666");
        when(response.getTitle()).thenReturn("Inception");
        when(response.getYear()).thenReturn("2010");
        when(response.getReleased()).thenReturn("16 Jul 2010");
        when(response.getRuntime()).thenReturn("148 min");
        when(response.getGenre()).thenReturn("Action, Adventure, Sci-Fi");
        when(response.getDirector()).thenReturn("Christopher Nolan");
        when(response.getActors()).thenReturn("Leonardo DiCaprio, Joseph Gordon-Levitt");
        when(response.getPlot()).thenReturn("A thief enters dreams.");
        when(response.getPoster()).thenReturn("https://example.com/inception.jpg");
        when(response.getImdbRating()).thenReturn("8.8");
        when(response.getType()).thenReturn("movie");

        FilmDetail result = mapper.toFilmDetail(response);

        assertNotNull(result);

        assertEquals("tt1375666", result.getImdbId());
        assertEquals("Inception", result.getTitle());

        assertEquals("2010", result.getYearText());
        assertEquals(2010, result.getReleaseYear());

        assertEquals(
                LocalDate.of(2010, 7, 16),
                result.getReleasedOn()
        );

        assertEquals(148, result.getRuntimeMinutes());

        assertEquals(
                "Action, Adventure, Sci-Fi",
                result.getGenre()
        );

        assertEquals(
                "Christopher Nolan",
                result.getDirector()
        );

        assertEquals(
                "Leonardo DiCaprio, Joseph Gordon-Levitt",
                result.getActors()
        );

        assertEquals(
                "A thief enters dreams.",
                result.getPlot()
        );

        assertEquals(
                "https://example.com/inception.jpg",
                result.getPosterUrl()
        );

        assertEquals(
                new BigDecimal("8.8"),
                result.getImdbRating()
        );

        assertEquals("movie", result.getType());
    }

    @Test
    void shouldConvertNaValuesToNull() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getImdbId()).thenReturn("tt1234567");
        when(response.getTitle()).thenReturn("Example");
        when(response.getYear()).thenReturn("N/A");
        when(response.getReleased()).thenReturn("N/A");
        when(response.getRuntime()).thenReturn("N/A");
        when(response.getGenre()).thenReturn("N/A");
        when(response.getDirector()).thenReturn("N/A");
        when(response.getActors()).thenReturn("N/A");
        when(response.getPlot()).thenReturn("N/A");
        when(response.getPoster()).thenReturn("N/A");
        when(response.getImdbRating()).thenReturn("N/A");
        when(response.getType()).thenReturn("movie");

        FilmDetail result = mapper.toFilmDetail(response);

        assertNull(result.getYearText());
        assertNull(result.getReleaseYear());
        assertNull(result.getReleasedOn());
        assertNull(result.getRuntimeMinutes());
        assertNull(result.getGenre());
        assertNull(result.getDirector());
        assertNull(result.getActors());
        assertNull(result.getPlot());
        assertNull(result.getPosterUrl());
        assertNull(result.getImdbRating());
    }

    @Test
    void shouldExtractFirstYearFromYearRange() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getYear()).thenReturn("2016–2019");

        FilmDetail result = mapper.toFilmDetail(response);

        assertEquals("2016–2019", result.getYearText());
        assertEquals(2016, result.getReleaseYear());
    }

    @Test
    void shouldReturnNullForMalformedValues() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getReleased()).thenReturn("not-a-date");
        when(response.getRuntime()).thenReturn("unknown");
        when(response.getImdbRating()).thenReturn("unknown");
        when(response.getPoster()).thenReturn("not-a-url");

        FilmDetail result = mapper.toFilmDetail(response);

        assertNull(result.getReleasedOn());
        assertNull(result.getRuntimeMinutes());
        assertNull(result.getImdbRating());
        assertNull(result.getPosterUrl());
    }

    @Test
    void shouldRejectUnsafePosterScheme() {

        OmdbDetailResponse response =
                mock(OmdbDetailResponse.class);

        when(response.getPoster())
                .thenReturn("javascript:alert(1)");

        FilmDetail result = mapper.toFilmDetail(response);

        assertNull(result.getPosterUrl());
    }

    @Test
    void shouldRejectNullResponse() {

        assertThrows(
                NullPointerException.class,
                () -> mapper.toFilmDetail(null)
        );
    }
}

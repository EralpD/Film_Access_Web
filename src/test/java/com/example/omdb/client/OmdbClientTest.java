package com.example.omdb.client;

import com.example.omdb.config.OmdbProperties;
import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.dto.OmdbSearchResponse;
import com.example.omdb.dto.OmdbType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpMethod;


class OmdbClientTest {

    private MockRestServiceServer server;

    private OmdbClient omdbClient;


    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer
            .bindTo(builder)
            .build();


        RestClient restClient = builder
            .baseUrl("https://www.omdbapi.com/")
            .build();


        OmdbProperties properties = new OmdbProperties();

        properties.setBaseUrl(
            "https://www.omdbapi.com/"
        );

        properties.setApiKey(
            "fake-test-api-key"
        );


        omdbClient = new OmdbClient(
            restClient,
            properties
        );
    }


    @Test
    void shouldSearchMoviesAndMapResponseCorrectly() {

        String responseBody = """
            {
              "Search": [
                {
                  "Title": "Batman Begins",
                  "Year": "2005",
                  "imdbID": "tt0372784",
                  "Type": "movie",
                  "Poster": "https://example.com/batman.jpg"
                }
              ],
              "totalResults": "1",
              "Response": "True"
            }
            """;


        server.expect(
                once(),
                requestTo(
                    "https://www.omdbapi.com/" +
                    "?apikey=fake-test-api-key" +
                    "&s=Batman" +
                    "&page=1"
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );


        OmdbSearchResponse response =
            omdbClient.search(
                "Batman",
                null,
                null,
                1
            );


        assertNotNull(response);

        assertTrue(
            response.isSuccessful()
        );

        assertEquals(
            "1",
            response.getTotalResults()
        );

        assertNotNull(
            response.getSearch()
        );

        assertEquals(
            1,
            response.getSearch().size()
        );

        assertEquals(
            "Batman Begins",
            response.getSearch()
                .get(0)
                .getTitle()
        );

        assertEquals(
            "2005",
            response.getSearch()
                .get(0)
                .getYear()
        );

        assertEquals(
            "tt0372784",
            response.getSearch()
                .get(0)
                .getImdbId()
        );

        assertEquals(
            "movie",
            response.getSearch()
                .get(0)
                .getType()
        );


        server.verify();
    }


    @Test
    void shouldAddYearAndTypeToSearchRequest() {

        String responseBody = """
            {
              "Search": [
                {
                  "Title": "Batman Begins",
                  "Year": "2005",
                  "imdbID": "tt0372784",
                  "Type": "movie",
                  "Poster": "N/A"
                }
              ],
              "totalResults": "1",
              "Response": "True"
            }
            """;


        server.expect(
                once(),
                requestTo(
                    "https://www.omdbapi.com/" +
                    "?apikey=fake-test-api-key" +
                    "&s=Batman" +
                    "&page=1" +
                    "&y=2005" +
                    "&type=movie"
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );


        OmdbSearchResponse response =
            omdbClient.search(
                "Batman",
                2005,
                OmdbType.MOVIE,
                1
            );


        assertNotNull(response);

        assertTrue(
            response.isSuccessful()
        );


        server.verify();
    }


    @Test
    void shouldMapOmdbFalseResponseCorrectly() {

        String responseBody = """
            {
              "Response": "False",
              "Error": "Movie not found!"
            }
            """;


        server.expect(
                once(),
                requestTo(
                    "https://www.omdbapi.com/" +
                    "?apikey=fake-test-api-key" +
                    "&s=SomethingThatDoesNotExist" +
                    "&page=1"
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );


        OmdbSearchResponse response =
            omdbClient.search(
                "SomethingThatDoesNotExist",
                null,
                null,
                1
            );


        assertNotNull(response);

        assertFalse(
            response.isSuccessful()
        );

        assertEquals(
            "Movie not found!",
            response.getError()
        );


        server.verify();
    }


    @Test
    void shouldRejectBlankSearchTitle() {

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> omdbClient.search(
                    "   ",
                    null,
                    null,
                    1
                )
            );


        assertEquals(
            "Search title cannot be blank.",
            exception.getMessage()
        );
    }


    @Test
    void shouldRejectPageBelowOne() {

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> omdbClient.search(
                    "Batman",
                    null,
                    null,
                    0
                )
            );


        assertEquals(
            "Page must be between 1 and 100.",
            exception.getMessage()
        );
    }


    @Test
    void shouldRejectPageAboveOneHundred() {

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> omdbClient.search(
                    "Batman",
                    null,
                    null,
                    101
                )
            );


        assertEquals(
            "Page must be between 1 and 100.",
            exception.getMessage()
        );
    }

    @Test
void shouldGetMovieDetailsAndMapResponseCorrectly() {

    String responseBody = """
        {
          "Title": "Batman Begins",
          "Year": "2005",
          "Rated": "PG-13",
          "Released": "15 Jun 2005",
          "Runtime": "140 min",
          "Genre": "Action, Crime, Drama",
          "Director": "Christopher Nolan",
          "Actors": "Christian Bale, Michael Caine",
          "Plot": "After training with his mentor, Batman begins his fight.",
          "Poster": "https://example.com/batman.jpg",
          "imdbRating": "8.2",
          "imdbID": "tt0372784",
          "Type": "movie",
          "Response": "True"
        }
        """;


    server.expect(
            once(),
            requestTo(
                "https://www.omdbapi.com/" +
                "?apikey=fake-test-api-key" +
                "&i=tt0372784" +
                "&plot=full"
            )
        )
        .andExpect(
            method(HttpMethod.GET)
        )
        .andRespond(
            withSuccess(
                responseBody,
                MediaType.APPLICATION_JSON
            )
        );


    OmdbDetailResponse response =
        omdbClient.getDetails(
            "tt0372784"
        );


    assertNotNull(response);

    assertTrue(
        response.isSuccessful()
    );

    assertEquals(
        "Batman Begins",
        response.getTitle()
    );

    assertEquals(
        "2005",
        response.getYear()
    );

    assertEquals(
        "140 min",
        response.getRuntime()
    );

    assertEquals(
        "Action, Crime, Drama",
        response.getGenre()
    );

    assertEquals(
        "Christopher Nolan",
        response.getDirector()
    );

    assertEquals(
        "8.2",
        response.getImdbRating()
    );

    assertEquals(
        "tt0372784",
        response.getImdbId()
    );

    assertEquals(
        "movie",
        response.getType()
    );


    server.verify();
    }
    @Test
void shouldRejectInvalidImdbIdBeforeSendingRequest() {

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> omdbClient.getDetails(
                "Batman"
            )
        );


    assertEquals(
        "Invalid IMDb ID.",
        exception.getMessage()
    );
}

@Test
void shouldMapFalseDetailResponseCorrectly() {

    String responseBody = """
        {
          "Response": "False",
          "Error": "Incorrect IMDb ID."
        }
        """;


    server.expect(
            once(),
            requestTo(
                "https://www.omdbapi.com/" +
                "?apikey=fake-test-api-key" +
                "&i=tt999999999999" +
                "&plot=full"
            )
        )
        .andExpect(
            method(HttpMethod.GET)
        )
        .andRespond(
            withSuccess(
                responseBody,
                MediaType.APPLICATION_JSON
            )
        );


    OmdbDetailResponse response =
        omdbClient.getDetails(
            "tt999999999999"
        );


    assertNotNull(response);

    assertFalse(
        response.isSuccessful()
    );

    assertEquals(
        "Incorrect IMDb ID.",
        response.getError()
    );


    server.verify();
    }


}


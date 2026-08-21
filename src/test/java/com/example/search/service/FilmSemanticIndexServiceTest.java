package com.example.search.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.film.Film;
import com.example.search.FilmEmbeddingRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmSemanticIndexServiceTest {

    @Mock
    private FilmEmbeddingService embeddingService;

    @Mock
    private FilmEmbeddingRepository embeddingRepository;

    @Mock
    private Film firstFilm;

    @Mock
    private Film secondFilm;

    private FilmSemanticIndexService service;


    @BeforeEach
    void setUp() {
        service = new FilmSemanticIndexService(
                embeddingService,
                embeddingRepository
        );
    }


    @Test
    void shouldNotCallEmbeddingApiWhenStoredEmbeddingIsCurrent() {

        when(firstFilm.getId()).thenReturn(1L);
        when(embeddingService.getEmbeddingModelName())
                .thenReturn("text-embedding-3-small");
        when(embeddingService.createContentHash(firstFilm))
                .thenReturn("hash-1");
        when(embeddingRepository.hasCurrentEmbedding(
                1L,
                "text-embedding-3-small",
                "hash-1"
        )).thenReturn(true);

        boolean indexed = service.indexFilm(firstFilm);

        assertFalse(indexed);
        verify(embeddingService, never())
                .createEmbeddings(List.of(firstFilm));
    }


    @Test
    void shouldBatchOnlyStaleFilmsIntoOneEmbeddingRequest() {

        when(firstFilm.getId()).thenReturn(1L);
        when(secondFilm.getId()).thenReturn(2L);
        when(embeddingService.getEmbeddingModelName())
                .thenReturn("text-embedding-3-small");
        when(embeddingService.createContentHash(firstFilm))
                .thenReturn("hash-1");
        when(embeddingService.createContentHash(secondFilm))
                .thenReturn("hash-2");

        when(embeddingRepository.hasCurrentEmbedding(
                1L,
                "text-embedding-3-small",
                "hash-1"
        )).thenReturn(true);

        when(embeddingRepository.hasCurrentEmbedding(
                2L,
                "text-embedding-3-small",
                "hash-2"
        )).thenReturn(false);

        float[] vector = new float[] {0.1f, 0.2f};

        when(embeddingService.createEmbeddings(
                List.of(secondFilm)
        )).thenReturn(List.of(vector));

        int indexed = service.indexFilms(
                List.of(firstFilm, secondFilm)
        );

        assertEquals(1, indexed);
        verify(embeddingService)
                .createEmbeddings(List.of(secondFilm));
        verify(embeddingRepository).updateEmbedding(
                2L,
                vector,
                "text-embedding-3-small",
                "hash-2"
        );
    }
}

package com.example.search.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.film.Film;
import com.example.search.FilmEmbeddingRepository;

@Service
public class FilmSemanticIndexService {

    private final FilmEmbeddingService embeddingService;
    private final FilmEmbeddingRepository embeddingRepository;

    /*
     * Tekli detay indeksleme ile toplu backfill aynı anda aynı film için
     * iki harici istek göndermesin diye indeksleme işlemleri birleştirilir.
     */
    private final Object indexingMonitor = new Object();


    public FilmSemanticIndexService(
            FilmEmbeddingService embeddingService,
            FilmEmbeddingRepository embeddingRepository
    ) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }


    public boolean indexFilm(Film film) {
        return indexFilms(List.of(film)) == 1;
    }


    public int indexFilms(List<Film> films) {

        if (films == null || films.isEmpty()) {
            return 0;
        }

        synchronized (indexingMonitor) {
            String model =
                    embeddingService.getEmbeddingModelName();

            List<PendingEmbedding> pending =
                    new ArrayList<>();

            for (Film film : films) {
                validatePersistedFilm(film);

                String contentHash =
                        embeddingService.createContentHash(film);

                if (!embeddingRepository.hasCurrentEmbedding(
                        film.getId(),
                        model,
                        contentHash
                )) {
                    pending.add(
                            new PendingEmbedding(
                                    film,
                                    contentHash
                            )
                    );
                }
            }

            if (pending.isEmpty()) {
                return 0;
            }

            List<Film> pendingFilms = pending.stream()
                    .map(PendingEmbedding::film)
                    .toList();

            /*
             * Spring AI liste girdisini tek embedding isteğinde gönderir.
             */
            List<float[]> embeddings =
                    embeddingService.createEmbeddings(pendingFilms);

            if (embeddings.size() != pending.size()) {
                throw new IllegalStateException(
                        "Embedding response size does not match request size."
                );
            }

            for (int index = 0; index < pending.size(); index++) {
                PendingEmbedding item = pending.get(index);

                embeddingRepository.updateEmbedding(
                        item.film().getId(),
                        embeddings.get(index),
                        model,
                        item.contentHash()
                );
            }

            return pending.size();
        }
    }


    public void reindexFilm(Film film) {
        indexFilm(film);
    }


    private void validatePersistedFilm(Film film) {
        if (film == null || film.getId() == null) {
            throw new IllegalArgumentException(
                    "Film must be persisted before indexing."
            );
        }
    }


    private record PendingEmbedding(
            Film film,
            String contentHash
    ) {
    }
}

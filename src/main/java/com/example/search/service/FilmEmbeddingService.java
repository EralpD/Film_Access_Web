package com.example.search.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.example.film.Film;

@Service
public class FilmEmbeddingService {

    private final EmbeddingModel embeddingModel;


    public FilmEmbeddingService(
            EmbeddingModel embeddingModel
    ) {
        this.embeddingModel = embeddingModel;
    }


    public float[] createEmbedding(Film film) {

        String searchText = buildSearchText(film);

        return embeddingModel.embed(searchText);
    }


    public float[] createQueryEmbedding(String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Semantic search query cannot be blank."
            );
        }

        return embeddingModel.embed(query.trim());
    }


    private String buildSearchText(Film film) {

        StringBuilder text = new StringBuilder();

        append(
                text,
                "Title",
                film.getTitle()
        );

        append(
                text,
                "Year",
                film.getYearText()
        );

        append(
                text,
                "Type",
                film.getType()
        );

        append(
                text,
                "Genres",
                film.getGenresText()
        );

        append(
                text,
                "Director",
                film.getDirector()
        );

        append(
                text,
                "Actors",
                film.getActors()
        );

        append(
                text,
                "Plot",
                film.getPlot()
        );

        return text.toString();
    }


    private void append(
            StringBuilder builder,
            String label,
            Object value
    ) {

        if (value == null) {
            return;
        }

        String stringValue = value.toString().trim();

        if (stringValue.isBlank()
                || stringValue.equalsIgnoreCase("N/A")) {
            return;
        }

        builder
                .append(label)
                .append(": ")
                .append(stringValue)
                .append(System.lineSeparator());
    }
}

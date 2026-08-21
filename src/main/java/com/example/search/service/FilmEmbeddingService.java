package com.example.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.film.Film;

@Service
public class FilmEmbeddingService {

    private final EmbeddingModel embeddingModel;

    private final String embeddingModelName;


    public FilmEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value(
                "${spring.ai.openai.embedding.options.model:text-embedding-3-small}"
            )
            String embeddingModelName
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingModelName = embeddingModelName;
    }


    public float[] createEmbedding(Film film) {

        String searchText = buildSearchText(film);

        return embeddingModel.embed(searchText);
    }


    public List<float[]> createEmbeddings(
            List<Film> films
    ) {

        if (films == null || films.isEmpty()) {
            return List.of();
        }

        List<String> searchTexts = films.stream()
                .map(this::buildSearchText)
                .toList();

        return embeddingModel.embed(searchTexts);
    }


    public float[] createQueryEmbedding(String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Semantic search query cannot be blank."
            );
        }

        return embeddingModel.embed(query.trim());
    }


    public String getEmbeddingModelName() {
        return embeddingModelName;
    }


    public String createContentHash(Film film) {

        String searchText = buildSearchText(film);

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    searchText.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
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

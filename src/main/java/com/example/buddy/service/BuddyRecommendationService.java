package com.example.buddy.service;

import java.time.Year;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.search.service.FilmEmbeddingService;
import com.example.user.User;
import com.example.user.UserRepository;

import com.example.buddy.BuddyFilmResponse;
import com.example.buddy.BuddyRecommendationResponse;
import com.example.buddy.FilmIntentInterpreter;
import com.example.buddy.record.FilmIntent;
import com.example.buddy.repository.BuddyFilmCandidate;
import com.example.buddy.repository.BuddyRecommendationRepository;

@Service
public class BuddyRecommendationService {

    private static final Set<String> ALLOWED_GENRES =
            Set.of(
                "action", "adventure", "animation",
                "biography", "comedy", "crime",
                "documentary", "drama", "family",
                "fantasy", "history", "horror",
                "music", "musical", "mystery",
                "romance", "sci-fi", "sport",
                "thriller", "war", "western"
            );

    private final FilmIntentInterpreter intentInterpreter;
    private final FilmEmbeddingService embeddingService;
    private final BuddyRecommendationRepository repository;
    private final UserRepository userRepository;
    private final int displayLimit;
    private final int candidateLimit;

    public BuddyRecommendationService(
            FilmIntentInterpreter intentInterpreter,
            FilmEmbeddingService embeddingService,
            BuddyRecommendationRepository repository,
            UserRepository userRepository,

            @Value(
                "${buddy.recommendations.display-limit:6}"
            )
            int displayLimit,

            @Value(
                "${buddy.recommendations.candidate-limit:48}"
            )
            int candidateLimit
    ) {
        this.intentInterpreter = intentInterpreter;
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.userRepository = userRepository;
        this.displayLimit = Math.max(1, displayLimit);
        this.candidateLimit = Math.max(
            this.displayLimit,
            candidateLimit
        );
    }

    public BuddyRecommendationResponse recommend(
            String email,
            String prompt
    ) {
        User user = userRepository
                .findByEmail(normalizeEmail(email))
                .orElseThrow(() ->
                    new IllegalStateException(
                        "Authenticated user was not found."
                    )
                );

        FilmIntent intent = normalizeIntent(
            intentInterpreter.interpret(prompt),
            prompt
        );

        if (intent.needsClarification()) {
            return new BuddyRecommendationResponse(
                "waiting",
                intent.clarificationQuestion(),
                intent.mood(),
                List.of()
            );
        }

        float[] queryEmbedding =
                embeddingService.createQueryEmbedding(
                    intent.semanticQuery()
                );

        List<BuddyFilmCandidate> candidates =
                repository.findCandidates(
                    user.getId(),
                    queryEmbedding,
                    intent,
                    candidateLimit
                );

        if (candidates.isEmpty()) {
            return new BuddyRecommendationResponse(
                "empty",
                "Bu tarife tam uyan bir film bulamadım. "
                + "Tür veya süre sınırını biraz genişletebiliriz.",
                intent.mood(),
                List.of()
            );
        }

        List<BuddyFilmResponse> films =
                candidates.stream()
                        .limit(displayLimit)
                        .map(this::toResponse)
                        .toList();

        return new BuddyRecommendationResponse(
            "results",
            "Yazdığındaki havayı yakaladım. "
            + "Bence önce bunlara bakmalısın.",
            intent.mood(),
            films
        );
    }

    private BuddyFilmResponse toResponse(
            BuddyFilmCandidate candidate
    ) {
        String matchLabel;

        if (candidate.totalScore() >= 0.75) {
            matchLabel = "Çok güçlü eşleşme";
        } else if (candidate.totalScore() >= 0.60) {
            matchLabel = "Güçlü eşleşme";
        } else {
            matchLabel = "Yakın eşleşme";
        }

        return new BuddyFilmResponse(
            candidate.imdbId(),
            candidate.title(),
            candidate.yearText(),
            candidate.type(),
            candidate.genresText(),
            candidate.posterUrl(),
            matchLabel
        );
    }

    private FilmIntent normalizeIntent(
            FilmIntent raw,
            String originalPrompt
    ) {
        List<String> included =
                normalizeGenres(raw.includeGenres());

        List<String> excluded =
                normalizeGenres(raw.excludeGenres())
                        .stream()
                        .filter(genre ->
                            !included.contains(genre)
                        )
                        .toList();

        int maximumYear =
                Year.now().getValue() + 1;

        int yearFrom =
                normalizeYear(raw.yearFrom(), maximumYear);

        int yearTo =
                normalizeYear(raw.yearTo(), maximumYear);

        if (yearFrom > 0
                && yearTo > 0
                && yearFrom > yearTo) {

            int temporary = yearFrom;
            yearFrom = yearTo;
            yearTo = temporary;
        }

        int maxRuntime =
                raw.maxRuntimeMinutes() <= 0
                    ? 0
                    : Math.max(
                        45,
                        Math.min(
                            raw.maxRuntimeMinutes(),
                            300
                        )
                    );

        String type = switch (
            safeText(raw.type())
                    .toUpperCase(Locale.ROOT)
        ) {
            case "MOVIE" -> "MOVIE";
            case "SERIES" -> "SERIES";
            default -> "ANY";
        };

        String semanticQuery =
                safeText(raw.semanticQuery());

        if (semanticQuery.isBlank()) {
            semanticQuery = originalPrompt.trim();
        }

        String mood = safeText(raw.mood());

        if (mood.isBlank()) {
            mood = "aradığın atmosfer";
        }

        String clarification =
                safeText(raw.clarificationQuestion());

        if (raw.needsClarification()
                && clarification.isBlank()) {

            clarification =
                "Daha hafif, heyecanlı veya duygusal "
                + "bir şey mi arıyorsun?";
        }

        return new FilmIntent(
            truncate(mood, 80),
            truncate(
                safeText(raw.desiredEffect()),
                120
            ),
            included,
            excluded,
            yearFrom,
            yearTo,
            maxRuntime,
            type,
            truncate(semanticQuery, 800),
            raw.needsClarification(),
            truncate(clarification, 180)
        );
    }

    private List<String> normalizeGenres(
            List<String> genres
    ) {
        if (genres == null) {
            return List.of();
        }

        LinkedHashSet<String> normalized =
                new LinkedHashSet<>();

        for (String genre : genres) {
            String value = safeText(genre)
                    .toLowerCase(Locale.ROOT);

            if (ALLOWED_GENRES.contains(value)) {
                normalized.add(value);
            }

            if (normalized.size() >= 6) {
                break;
            }
        }

        return List.copyOf(normalized);
    }

    private int normalizeYear(
            int year,
            int maximumYear
    ) {
        if (year <= 0) {
            return 0;
        }

        return Math.max(
            1888,
            Math.min(year, maximumYear)
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                "User email cannot be empty."
            );
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    private String truncate(
            String value,
            int maximumLength
    ) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }
}
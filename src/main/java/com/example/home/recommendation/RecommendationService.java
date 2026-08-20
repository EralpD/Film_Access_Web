package com.example.home.recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.user.User;
import com.example.user.UserRepository;

@Service
public class RecommendationService {

    // Maximum two main category can be displayyed. This rule isn't flexible.

    private final UserRepository userRepository;

    private final RecommendationRepository
            recommendationRepository;

    private final int displayLimit;

    private final int candidateLimit;

    private final int maxPerPrimaryGenre;

    private final double minSimilarity;


    public RecommendationService(
            UserRepository userRepository,
            RecommendationRepository recommendationRepository,

            @Value(
                "${home.recommendations.display-limit:6}"
            )
            int displayLimit,

            @Value(
                "${home.recommendations.candidate-limit:48}"
            )
            int candidateLimit,

            @Value(
                "${home.recommendations.max-per-primary-genre:2}"
            )
            int maxPerPrimaryGenre,

            @Value(
                "${home.recommendations.min-similarity:0.25}"
            )
            double minSimilarity
    ) {

        this.userRepository =
                userRepository;

        this.recommendationRepository =
                recommendationRepository;

        this.displayLimit =
                Math.max(1, displayLimit);

        this.candidateLimit =
                Math.max(
                        this.displayLimit,
                        candidateLimit
                );

        this.maxPerPrimaryGenre =
                Math.max(
                        1,
                        maxPerPrimaryGenre
                );

        this.minSimilarity =
                Math.max(
                        -1.0,
                        Math.min(
                                minSimilarity,
                                1.0
                        )
                );
    }


    @Transactional(readOnly = true)
    public List<RecommendedFilmResponse> recommendFor(
            String userEmail
    ) {

        User user =
                userRepository
                        .findByEmail(
                                normalizeEmail(
                                        userEmail
                                )
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Authenticated user was not found."
                                )
                        );

        List<RecommendationCandidate> candidates =
                recommendationRepository
                        .findCandidates(
                                user.getId(),
                                minSimilarity,
                                candidateLimit
                        );

        /*
         * Cold-start fallback uygulanmıyor.
         * Profil veya aday yoksa sonuç boş kalır.
         */
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> genreCounts =
                new HashMap<>();

        List<RecommendedFilmResponse> selected =
                new ArrayList<>();

        for (RecommendationCandidate candidate :
                candidates) {

            String primaryGenre =
                    primaryGenre(
                            candidate.genresText()
                    );

            if (primaryGenre != null) {

                int currentCount =
                        genreCounts.getOrDefault(
                                primaryGenre,
                                0
                        );

                if (currentCount
                        >= maxPerPrimaryGenre) {

                    continue;
                }

                genreCounts.put(
                        primaryGenre,
                        currentCount + 1
                );
            }

            selected.add(
                    RecommendedFilmResponse.from(
                            candidate
                    )
            );

            if (selected.size()
                    >= displayLimit) {

                break;
            }
        }

        return List.copyOf(
                selected
        );
    }


    private String primaryGenre(
            String genresText
    ) {

        if (genresText == null
                || genresText.isBlank()) {

            return null;
        }

        String primaryGenre =
                genresText
                        .split(",", 2)[0]
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return primaryGenre.isBlank()
                ? null
                : primaryGenre;
    }


    private String normalizeEmail(
            String email
    ) {

        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "User email cannot be empty."
            );
        }

        return email
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
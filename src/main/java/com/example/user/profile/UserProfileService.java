package com.example.user.profile;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.archive.UserFilmRepository;
import com.example.user.User;
import com.example.user.UserRepository;

@Service
public class UserProfileService {

    private static final DateTimeFormatter MEMBER_SINCE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);

    private static final Map<String, String> GENRE_COLORS = Map.ofEntries(
            Map.entry("action", "#ef6351"),
            Map.entry("adventure", "#f2b84b"),
            Map.entry("animation", "#9b7ede"),
            Map.entry("biography", "#68b984"),
            Map.entry("comedy", "#f4d35e"),
            Map.entry("crime", "#c44569"),
            Map.entry("documentary", "#40b3a2"),
            Map.entry("drama", "#5b8def"),
            Map.entry("family", "#71c4ef"),
            Map.entry("fantasy", "#8f6ccf"),
            Map.entry("history", "#9aa44f"),
            Map.entry("horror", "#a63d40"),
            Map.entry("music", "#db6fc4"),
            Map.entry("musical", "#d45ee5"),
            Map.entry("mystery", "#6670c4"),
            Map.entry("romance", "#ed7da6"),
            Map.entry("sci-fi", "#35b6d4"),
            Map.entry("science fiction", "#35b6d4"),
            Map.entry("sport", "#8fcf5b"),
            Map.entry("thriller", "#e58b45"),
            Map.entry("war", "#8491a3"),
            Map.entry("western", "#b27a4b"),
            Map.entry("uncategorized", "#697386")
    );

    private static final List<String> FALLBACK_COLORS = List.of(
            "#55a6d9", "#d9795f", "#75b798", "#b482d9", "#d4a64a", "#6f8bd8"
    );

    private final UserRepository userRepository;
    private final UserFilmRepository userFilmRepository;

    public UserProfileService(UserRepository userRepository, UserFilmRepository userFilmRepository) {
        this.userRepository = userRepository;
        this.userFilmRepository = userFilmRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String authenticatedEmail, Long requestedUserId) {
        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user no longer exists."));

        if (!user.getId().equals(requestedUserId)) {
            throw new AccessDeniedException("You cannot view another user's details.");
        }

        var userFilms = userFilmRepository.findAllByUser_IdOrderByAddedAtDesc(user.getId());
        Map<String, Long> counts = new LinkedHashMap<>();

        userFilms.forEach(userFilm -> {
            String genresText = userFilm.getFilm().getGenresText();
            if (genresText == null || genresText.isBlank() || "N/A".equalsIgnoreCase(genresText.trim())) {
                counts.merge("Uncategorized", 1L, Long::sum);
                return;
            }

            List<String> genres = java.util.Arrays.stream(genresText.split(","))
                    .map(String::trim)
                    .filter(genre -> !genre.isBlank() && !"N/A".equalsIgnoreCase(genre))
                    .distinct()
                    .toList();

            if (genres.isEmpty()) {
                counts.merge("Uncategorized", 1L, Long::sum);
            } else {
                genres.forEach(genre -> counts.merge(genre, 1L, Long::sum));
            }
        });

        List<Map.Entry<String, Long>> sortedGenres = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        long genreTagCount = sortedGenres.stream().mapToLong(Map.Entry::getValue).sum();
        List<GenreBreakdownItem> breakdown = buildBreakdown(sortedGenres, genreTagCount);
        String memberSince = user.getCreatedAt() == null
                ? "Not available"
                : MEMBER_SINCE_FORMAT.format(user.getCreatedAt());

        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                titleCase(user.getRole().name()),
                memberSince,
                userFilms.size(),
                genreTagCount,
                breakdown
        );
    }

    private List<GenreBreakdownItem> buildBreakdown(
            List<Map.Entry<String, Long>> genres,
            long total
    ) {
        if (total == 0) {
            return List.of();
        }

        List<GenreBreakdownItem> result = new ArrayList<>();
        double offset = 0;
        for (int index = 0; index < genres.size(); index++) {
            var genre = genres.get(index);
            double percentage = genre.getValue() * 100.0 / total;
            result.add(new GenreBreakdownItem(
                    genre.getKey(),
                    genre.getValue(),
                    percentage,
                    offset,
                    String.format(Locale.ENGLISH, "%.1f%%", percentage),
                    colorFor(genre.getKey(), index)
            ));
            offset += percentage;
        }
        return List.copyOf(result);
    }

    private String colorFor(String genre, int index) {
        return GENRE_COLORS.getOrDefault(
                genre.toLowerCase(Locale.ENGLISH),
                FALLBACK_COLORS.get(index % FALLBACK_COLORS.size())
        );
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

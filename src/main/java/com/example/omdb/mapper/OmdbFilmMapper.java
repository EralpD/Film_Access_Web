package com.example.omdb.mapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.omdb.dto.OmdbDetailResponse;
import com.example.omdb.model.FilmDetail;

@Component
public class OmdbFilmMapper {

    private static final Pattern YEAR_PATTERN =
            Pattern.compile("(\\d{4})");

    private static final Pattern RUNTIME_PATTERN =
            Pattern.compile("(\\d+)\\s*min", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter RELEASE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    public FilmDetail toFilmDetail(OmdbDetailResponse response) {

        Objects.requireNonNull(response, "OMDb film detail response must not be null");

        String yearText = normalizeText(response.getYear());

        return new FilmDetail(
                normalizeText(response.getImdbId()),
                normalizeText(response.getTitle()),
                yearText,
                parseReleaseYear(yearText),
                parseReleasedDate(response.getReleased()),
                parseRuntimeMinutes(response.getRuntime()),
                normalizeText(response.getGenre()),
                normalizeText(response.getDirector()),
                normalizeText(response.getActors()),
                normalizeText(response.getPlot()),
                normalizePosterUrl(response.getPoster()),
                parseImdbRating(response.getImdbRating()),
                normalizeText(response.getType())
        );
    }

    private String normalizeText(String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isEmpty() || "N/A".equalsIgnoreCase(normalized)) {
            return null;
        }

        return normalized;
    }

    private Integer parseReleaseYear(String yearText) {

        if (yearText == null) {
            return null;
        }

        Matcher matcher = YEAR_PATTERN.matcher(yearText);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate parseReleasedDate(String value) {

        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        try {
            return LocalDate.parse(normalized, RELEASE_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Integer parseRuntimeMinutes(String value) {

        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        Matcher matcher = RUNTIME_PATTERN.matcher(normalized);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal parseImdbRating(String value) {

        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        try {
            BigDecimal rating = new BigDecimal(normalized);

            if (rating.compareTo(BigDecimal.ZERO) < 0
                    || rating.compareTo(BigDecimal.TEN) > 0) {
                return null;
            }

            return rating;

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizePosterUrl(String value) {

        String normalized = normalizeText(value);

        if (normalized == null) {
            return null;
        }

        try {
            URI uri = URI.create(normalized);

            String scheme = uri.getScheme();

            if (scheme == null) {
                return null;
            }

            if (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https")) {
                return null;
            }

            return normalized;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

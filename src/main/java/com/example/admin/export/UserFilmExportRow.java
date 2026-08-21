package com.example.admin.export;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UserFilmExportRow(
        long userFilmId,
        long userId,
        String displayName,
        String email,
        OffsetDateTime addedAt,
        long filmId,
        String imdbId,
        String title,
        String yearText,
        String type,
        String genresText,
        BigDecimal imdbRating
) {
}
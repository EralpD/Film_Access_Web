package com.example.buddy.record;

import java.util.List;

public record FilmIntent(

        String mood,
        String desiredEffect,
        List<String> includeGenres,
        List<String> excludeGenres,

        // 0 değeri: filtre belirtilmedi.
        int yearFrom,
        int yearTo,
        int maxRuntimeMinutes,

        // ANY, MOVIE veya SERIES
        String type,

        // Embedding için İngilizce semantik açıklama
        String semanticQuery,

        boolean needsClarification,
        String clarificationQuestion

) {
}
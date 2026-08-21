package com.example.admin.export;

import java.time.OffsetDateTime;

public record AdminExportSummary(
        OffsetDateTime generatedAt,
        String generatedBy,
        long totalUsers,
        long enabledUsers,
        long disabledUsers,
        long totalArchiveRelations,
        long distinctArchivedFilms
) {
}
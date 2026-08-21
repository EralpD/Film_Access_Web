package com.example.admin.export;

import java.time.OffsetDateTime;

import com.example.user.UserRole;

public record UserExportRow(
        long userId,
        String displayName,
        String email,
        UserRole role,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long archiveFilmCount,
        OffsetDateTime lastArchiveAddition
) {
}
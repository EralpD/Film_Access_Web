package com.example.user.profile;

import java.util.List;

public record UserProfileResponse(
        Long userId,
        String displayName,
        String email,
        String role,
        String memberSince,
        long filmCount,
        long genreTagCount,
        List<GenreBreakdownItem> genres
) {
}

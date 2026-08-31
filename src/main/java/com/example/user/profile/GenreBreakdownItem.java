package com.example.user.profile;

public record GenreBreakdownItem(
        String name,
        long count,
        double percentage,
        double offset,
        String percentageLabel,
        String color
) {
}

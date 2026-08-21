package com.example.buddy;

import java.util.List;

public record BuddyRecommendationResponse(

        // results, waiting veya empty
        String state,
        String message,
        String mood,
        List<BuddyFilmResponse> films

) {
} 


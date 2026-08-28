package com.example.buddy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuddyRecommendationRequest(

        @NotBlank(message = "Please describe what you would like to watch.")
        @Size(
            max = 500,
            message = "Your request must be no longer than 500 characters."
        )
        String prompt

) {
}
package com.example.buddy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuddyRecommendationRequest(

        @NotBlank(message = "Ne izlemek istediğini yazmalısın.")
        @Size(
            max = 500,
            message = "İstek en fazla 500 karakter olabilir."
        )
        String prompt

) {
}
package com.canmakan.backend.analytics.dto;

public record FlaggedIngredientTrend(
        String ingredientName,
        long flaggedCount
) {
}

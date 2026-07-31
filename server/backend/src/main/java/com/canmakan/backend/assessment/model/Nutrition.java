package com.canmakan.backend.assessment.model;

import java.math.BigDecimal;

/**
 * Represents per-100g nutrition data used by the dietary rule engine.
 *
 * @author YangMaowei
 */
public record Nutrition(
        BigDecimal sugarsPer100g,
        BigDecimal sodiumPer100g,
        BigDecimal transFatPer100g,
        BigDecimal saturatedFatPer100g,
        BigDecimal fatPer100g,
        BigDecimal energyKcalPer100g
) {
}

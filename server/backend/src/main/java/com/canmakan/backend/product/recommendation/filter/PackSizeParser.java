package com.canmakan.backend.product.recommendation.filter;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses pack / serving volume from catalog quantity fields for milk substitute ranking.
 */
public final class PackSizeParser {

    public static final double PACK_SIZE_WEIGHT = 0.08;
    private static final double MAX_PACK_RANGE_ML = 1000.0;
    private static final double MIN_PACK_MATCH_SIMILARITY = 0.85;

    private PackSizeParser() {
    }

    /**
     * Resolves pack volume in millilitres from {@code quantity}, then {@code serving_size},
     * then {@code serving_quantity} when it looks like a drink serving (not grams).
     */
    public static Optional<Double> resolveVolumeMl(CatalogProduct product) {
        if (product == null) {
            return Optional.empty();
        }
        Optional<Double> fromQuantity = parseVolumeMl(product.getQuantity());
        if (fromQuantity.isPresent()) {
            return fromQuantity;
        }
        Optional<Double> fromServingSize = parseVolumeMl(product.getServingSize());
        if (fromServingSize.isPresent()) {
            return fromServingSize;
        }
        return resolveServingQuantityMl(product.getServingQuantity());
    }

    public static Optional<Double> parseVolumeMl(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        int searchFrom = 0;
        while (searchFrom < normalized.length()) {
            int index = searchFrom;
            while (index < normalized.length() && !Character.isDigit(normalized.charAt(index))) {
                index++;
            }
            if (index >= normalized.length()) {
                return Optional.empty();
            }
            int amountStart = index;
            while (index < normalized.length()) {
                char character = normalized.charAt(index);
                if (!Character.isDigit(character) && character != '.') {
                    break;
                }
                index++;
            }
            double amount;
            try {
                amount = Double.parseDouble(normalized.substring(amountStart, index));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            int unitStart = index;
            while (unitStart < normalized.length() && Character.isWhitespace(normalized.charAt(unitStart))) {
                unitStart++;
            }
            Optional<Double> volume = volumeMlForUnit(amount, normalized.substring(unitStart));
            if (volume.isPresent()) {
                return volume;
            }
            searchFrom = index == amountStart ? index + 1 : index;
        }
        return Optional.empty();
    }

    /**
     * Similarity in {@code [0, 1]} from pack volume closeness; 0 when either side is unknown.
     */
    public static double similarity(CatalogProduct source, CatalogProduct candidate) {
        Optional<Double> sourceMl = resolveVolumeMl(source);
        Optional<Double> candidateMl = resolveVolumeMl(candidate);
        if (sourceMl.isEmpty() || candidateMl.isEmpty()) {
            return 0.0;
        }
        double deltaMl = Math.abs(sourceMl.get() - candidateMl.get());
        return 1.0 - Math.min(1.0, deltaMl / MAX_PACK_RANGE_ML);
    }

    public static double weightedBoost(CatalogProduct source, CatalogProduct candidate) {
        return similarity(source, candidate) * PACK_SIZE_WEIGHT;
    }

    public static boolean isStrongPackSizeMatch(CatalogProduct source, CatalogProduct candidate) {
        return similarity(source, candidate) >= MIN_PACK_MATCH_SIMILARITY;
    }

    private static Optional<Double> volumeMlForUnit(double amount, String unit) {
        if (unit.startsWith("ml") || unit.startsWith("millilitre") || unit.startsWith("milliliter")) {
            return Optional.of(amount);
        }
        if (unit.startsWith("cl")) {
            return Optional.of(amount * 10.0);
        }
        if (unit.startsWith("litre") || unit.startsWith("liter") || isBareLitre(unit)) {
            return Optional.of(amount * 1000.0);
        }
        return Optional.empty();
    }

    private static boolean isBareLitre(String unit) {
        if (unit.isEmpty() || unit.charAt(0) != 'l') {
            return false;
        }
        return unit.length() == 1 || !Character.isLetter(unit.charAt(1));
    }

    private static Optional<Double> resolveServingQuantityMl(BigDecimal servingQuantity) {
        if (servingQuantity == null) {
            return Optional.empty();
        }
        double value = servingQuantity.doubleValue();
        if (value <= 0 || value > 5000) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

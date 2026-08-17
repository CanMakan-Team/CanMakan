package com.canmakan.backend.product.recommendation;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses pack / serving volume from catalog quantity fields for milk substitute ranking.
 */
final class PackSizeParser {

    static final double PACK_SIZE_WEIGHT = 0.08;
    private static final double MAX_PACK_RANGE_ML = 1000.0;
    private static final double MIN_PACK_MATCH_SIMILARITY = 0.85;

    private static final Pattern MILLILITRE_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(ml|mL|millilitre?s?|milliliter?s?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LITRE_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(l|litre?s?|liter?s?)(?![a-z])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CENTILITRE_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(cl)",
            Pattern.CASE_INSENSITIVE);

    private PackSizeParser() {
    }

    /**
     * Resolves pack volume in millilitres from {@code quantity}, then {@code serving_size},
     * then {@code serving_quantity} when it looks like a drink serving (not grams).
     */
    static Optional<Double> resolveVolumeMl(CatalogProduct product) {
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

    static Optional<Double> parseVolumeMl(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        Matcher millilitreMatcher = MILLILITRE_PATTERN.matcher(normalized);
        if (millilitreMatcher.find()) {
            return Optional.of(parseAmount(millilitreMatcher.group(1)));
        }
        Matcher litreMatcher = LITRE_PATTERN.matcher(normalized);
        if (litreMatcher.find()) {
            return Optional.of(parseAmount(litreMatcher.group(1)) * 1000.0);
        }
        Matcher centilitreMatcher = CENTILITRE_PATTERN.matcher(normalized);
        if (centilitreMatcher.find()) {
            return Optional.of(parseAmount(centilitreMatcher.group(1)) * 10.0);
        }
        return Optional.empty();
    }

    /**
     * Similarity in {@code [0, 1]} from pack volume closeness; 0 when either side is unknown.
     */
    static double similarity(CatalogProduct source, CatalogProduct candidate) {
        Optional<Double> sourceMl = resolveVolumeMl(source);
        Optional<Double> candidateMl = resolveVolumeMl(candidate);
        if (sourceMl.isEmpty() || candidateMl.isEmpty()) {
            return 0.0;
        }
        double deltaMl = Math.abs(sourceMl.get() - candidateMl.get());
        return 1.0 - Math.min(1.0, deltaMl / MAX_PACK_RANGE_ML);
    }

    static double weightedBoost(CatalogProduct source, CatalogProduct candidate) {
        return similarity(source, candidate) * PACK_SIZE_WEIGHT;
    }

    static boolean isStrongPackSizeMatch(CatalogProduct source, CatalogProduct candidate) {
        return similarity(source, candidate) >= MIN_PACK_MATCH_SIMILARITY;
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

    private static double parseAmount(String raw) {
        return Double.parseDouble(raw.replace(',', '.'));
    }
}

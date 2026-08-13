package com.canmakan.backend.product.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads precomputed TF-IDF vectors from the Python offline pipeline
 * ({@code product_feature_vectors.json}). When no artifact is configured or load fails,
 * online encoding falls back to {@link ProductFeatureEncoder}.
 */
@Slf4j
@Component
class ProductFeatureVectorStore {

    private final ObjectMapper objectMapper;
    private final String artifactPath;
    private Map<String, Map<String, Double>> vectorsByBarcode = Map.of();

    ProductFeatureVectorStore(
            ObjectMapper objectMapper,
            @Value("${canmakan.recommendation.ml.artifact-path:}") String artifactPath) {
        this.objectMapper = objectMapper;
        this.artifactPath = artifactPath == null ? "" : artifactPath.trim();
        reload();
    }

    void reload() {
        if (artifactPath.isEmpty()) {
            vectorsByBarcode = Map.of();
            return;
        }

        Path path = Path.of(artifactPath);
        if (!Files.isRegularFile(path)) {
            log.info("Tier C ML artifact not found at {}; using inline encoding.", path);
            vectorsByBarcode = Map.of();
            return;
        }

        try {
            vectorsByBarcode = parseArtifact(objectMapper.readTree(Files.readString(path)));
            log.info("Loaded Tier C ML artifact from {} ({} products).", path, vectorsByBarcode.size());
        } catch (IOException exception) {
            log.warn("Failed to load Tier C ML artifact from {}: {}", path, exception.getMessage());
            vectorsByBarcode = Map.of();
        }
    }

    boolean isLoaded() {
        return !vectorsByBarcode.isEmpty();
    }

    Optional<Map<String, Double>> getVector(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        Map<String, Double> vector = vectorsByBarcode.get(barcode.trim());
        if (vector == null || vector.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(vector);
    }

    private static Map<String, Map<String, Double>> parseArtifact(JsonNode root) {
        JsonNode products = root.path("products");
        if (!products.isObject()) {
            return Map.of();
        }

        Map<String, Map<String, Double>> parsed = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = products.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            Map<String, Double> vector = parseVector(entry.getValue());
            if (!vector.isEmpty()) {
                parsed.put(entry.getKey(), vector);
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    private static Map<String, Double> parseVector(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Double> vector = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isNumber()) {
                vector.put(entry.getKey(), entry.getValue().doubleValue());
            }
        }
        return vector;
    }
}

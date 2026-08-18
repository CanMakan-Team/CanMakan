package com.canmakan.backend.product.recommendation.ranking;

import java.util.Map;
import java.util.Set;

/**
 * Cosine similarity for sparse term-weight vectors (content-based UC5 prototype).
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    static double between(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        double dot = 0.0;
        Set<String> shared = left.keySet();
        for (String term : shared) {
            Double rightWeight = right.get(term);
            if (rightWeight != null) {
                dot += left.get(term) * rightWeight;
            }
        }

        double leftNorm = norm(left);
        double rightNorm = norm(right);
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (leftNorm * rightNorm);
    }

    private static double norm(Map<String, Double> vector) {
        double sum = 0.0;
        for (double weight : vector.values()) {
            sum += weight * weight;
        }
        return Math.sqrt(sum);
    }
}

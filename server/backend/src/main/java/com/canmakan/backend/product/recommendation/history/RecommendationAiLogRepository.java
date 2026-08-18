package com.canmakan.backend.product.recommendation.history;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries {@code recommendation_ai_logs}.
 */
public interface RecommendationAiLogRepository extends JpaRepository<RecommendationAiLog, Long> {
}

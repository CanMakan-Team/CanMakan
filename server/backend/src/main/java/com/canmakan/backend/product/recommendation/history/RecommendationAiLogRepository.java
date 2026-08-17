package com.canmakan.backend.product.recommendation.history;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries {@code recommendation_ai_logs}.
 */
public interface RecommendationAiLogRepository extends JpaRepository<RecommendationAiLog, Long> {

    List<RecommendationAiLog> findByScanId(Long scanId);
}

package com.canmakan.backend.product.recommendation.history;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries {@code recommendation_logs} for UC5 and UC17.
 */
public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    List<RecommendationLog> findByProfileIdAndShownToUserTrueOrderByCreatedAtDesc(Long profileId);
}

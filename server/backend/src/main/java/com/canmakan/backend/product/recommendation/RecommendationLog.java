package com.canmakan.backend.product.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One alternative product shown (or ranked) for a profile at recommendation time.
 * Powers UC5 logging and UC17 recommendation history.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recommendation_logs")
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "scan_id")
    private Long scanId;

    @Column(name = "source_barcode", nullable = false, length = 50)
    private String sourceBarcode;

    @Column(name = "recommended_barcode", nullable = false, length = 50)
    private String recommendedBarcode;

    @Column(name = "recommended_name", nullable = false)
    private String recommendedName;

    @Column(name = "recommended_brand")
    private String recommendedBrand;

    @Column(name = "discovery_tier", nullable = false, length = 30)
    private String discoveryTier;

    @Column(name = "verification_tier", nullable = false, length = 30)
    private String verificationTier = "TIER_1_RULES";

    @Column(name = "rank_score", precision = 8, scale = 4)
    private BigDecimal rankScore;

    @Column(name = "match_reason", length = 100)
    private String matchReason;

    @Column(name = "data_quality", nullable = false, length = 20)
    private String dataQuality = RecommendationDataQuality.VERIFIED.name();

    @Column(name = "verdict_at_recommendation", nullable = false, length = 20)
    private String verdictAtRecommendation = "SAFE";

    @Column(name = "shown_to_user", nullable = false)
    private boolean shownToUser = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

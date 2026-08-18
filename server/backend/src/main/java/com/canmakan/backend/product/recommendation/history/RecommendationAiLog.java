package com.canmakan.backend.product.recommendation.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Audit record for a Tier-B LLM discovery run during recommendation.
 * Mirrors {@code ai_execution_logs} but scoped to UC5 discovery.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recommendation_ai_logs")
public class RecommendationAiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private Long scanId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "source_barcode", nullable = false, length = 50)
    private String sourceBarcode;

    @Column(name = "execution_tier", nullable = false, length = 30)
    private String executionTier;

    @Column(name = "model_id", length = 50)
    private String modelId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_candidates_json")
    private String llmCandidatesJson;

    @Column(name = "candidates_accepted")
    private Integer candidatesAccepted;

    @Column(name = "candidates_rejected")
    private Integer candidatesRejected;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

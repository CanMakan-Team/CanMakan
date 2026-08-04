package com.canmakan.backend.ai.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Audit/diagnostic record of one assessment execution (rules fast-path or LLM).
 * Maps to the {@code ai_execution_logs} table.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_execution_logs")
public class AiExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private Long scanId;

    @Column(name = "execution_tier", nullable = false, length = 30)
    private String executionTier;           // "TIER_1_RULES" / "TIER_3_LLM"

    @Column(name = "model_id", length = 50)
    private String modelId;                 // nullable for rules-only

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compiled_prompt")
    private String compiledPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_llm_response")
    private String rawLlmResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

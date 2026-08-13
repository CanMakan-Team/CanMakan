package com.canmakan.backend.product.recommendation;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Best-effort persistence for UC5 recommendation results and UC17 history.
 */
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationLogService.class);

    private final RecommendationLogRepository recommendationLogRepository;
    private final RecommendationAiLogRepository recommendationAiLogRepository;

    /**
     * Log one alternative returned by Tier A (catalog) or Tier B (LLM + verified).
     */
    public RecommendationLog recordAlternative(RecommendationLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!validEntry(entry)) {
            LOGGER.warn("Skipped recommendation log because required fields were invalid.");
            return null;
        }

        RecommendationLog log = new RecommendationLog();
        log.setProfileId(entry.profileId());
        log.setScanId(entry.scanId());
        log.setSourceBarcode(entry.sourceBarcode().trim());
        log.setRecommendedBarcode(entry.recommendedBarcode().trim());
        log.setRecommendedName(entry.recommendedName().trim());
        log.setRecommendedBrand(trimToNull(entry.recommendedBrand()));
        log.setDiscoveryTier(entry.discoveryTier().name());
        log.setVerificationTier("TIER_1_RULES");
        log.setRankScore(entry.rankScore());
        log.setMatchReason(trimToNull(entry.matchReason()));
        log.setDataQuality(
                entry.dataQuality() == null
                        ? RecommendationDataQuality.VERIFIED.name()
                        : entry.dataQuality().name()
        );
        log.setVerdictAtRecommendation("SAFE");
        log.setShownToUser(entry.shownToUser());
        log.setCreatedAt(LocalDateTime.now());

        return saveAlternativeSafely(log);
    }

    /**
     * Log several alternatives from one recommendation request.
     */
    public void recordAlternatives(List<RecommendationLogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> seenBarcodes = new LinkedHashSet<>();
        for (RecommendationLogEntry entry : entries) {
            if (entry.recommendedBarcode() == null
                    || !seenBarcodes.add(entry.recommendedBarcode().trim())) {
                continue;
            }
            recordAlternative(entry);
        }
    }

    /**
     * Log one Tier-B LLM discovery audit row (prompt/response metadata only).
     */
    public RecommendationAiLog recordDiscoveryAudit(RecommendationDiscoveryAudit audit) {
        Objects.requireNonNull(audit, "audit");
        if (!validAudit(audit)) {
            LOGGER.warn("Skipped recommendation AI audit log because required fields were invalid.");
            return null;
        }

        RecommendationAiLog log = new RecommendationAiLog();
        log.setScanId(audit.scanId());
        log.setProfileId(audit.profileId());
        log.setSourceBarcode(audit.sourceBarcode().trim());
        log.setExecutionTier(RecommendationDiscoveryTier.TIER_B_LLM_DISCOVERY.name());
        log.setModelId(trimToNull(audit.modelId()));
        log.setPromptTokens(audit.promptTokens());
        log.setCompletionTokens(audit.completionTokens());
        log.setLatencyMs(safeLatency(audit.latencyMs()));
        log.setLlmCandidatesJson(audit.llmCandidatesJson());
        log.setCandidatesAccepted(Math.max(audit.candidatesAccepted(), 0));
        log.setCandidatesRejected(Math.max(audit.candidatesRejected(), 0));
        log.setCreatedAt(LocalDateTime.now());

        return saveAuditSafely(log);
    }

    /** UC17: list past recommendations shown to the user for a profile. */
    public List<RecommendationLog> listHistoryForProfile(Long profileId) {
        if (profileId == null || profileId <= 0) {
            return List.of();
        }
        return recommendationLogRepository.findByProfileIdAndShownToUserTrueOrderByCreatedAtDesc(profileId);
    }

    private boolean validEntry(RecommendationLogEntry entry) {
        return entry.profileId() != null
                && entry.profileId() > 0
                && hasText(entry.sourceBarcode())
                && hasText(entry.recommendedBarcode())
                && hasText(entry.recommendedName())
                && entry.discoveryTier() != null;
    }

    private boolean validAudit(RecommendationDiscoveryAudit audit) {
        return audit.scanId() != null
                && audit.scanId() > 0
                && audit.profileId() != null
                && audit.profileId() > 0
                && hasText(audit.sourceBarcode())
                && audit.latencyMs() >= 0;
    }

    private RecommendationLog saveAlternativeSafely(RecommendationLog log) {
        try {
            return recommendationLogRepository.save(log);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Recommendation log persistence failed for profileId={}, sourceBarcode={}, errorType={}",
                    log.getProfileId(),
                    log.getSourceBarcode(),
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private RecommendationAiLog saveAuditSafely(RecommendationAiLog log) {
        try {
            return recommendationAiLogRepository.save(log);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Recommendation AI audit persistence failed for scanId={}, errorType={}",
                    log.getScanId(),
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int safeLatency(long latencyMs) {
        if (latencyMs < 0) {
            return 0;
        }
        return latencyMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latencyMs;
    }
}

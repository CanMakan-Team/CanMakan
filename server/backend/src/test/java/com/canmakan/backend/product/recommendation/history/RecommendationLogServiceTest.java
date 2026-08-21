package com.canmakan.backend.product.recommendation.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.product.recommendation.discovery.RecommendationDiscoveryAudit;
import com.canmakan.backend.product.recommendation.discovery.RecommendationDiscoveryTier;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class RecommendationLogServiceTest {

    @Mock
    private RecommendationLogRepository recommendationLogRepository;

    @Mock
    private RecommendationAiLogRepository recommendationAiLogRepository;

    @InjectMocks
    private RecommendationLogService service;

    @Test
    void recordAlternativePersistsTrimmedFieldsAndUtcTimestamp() {
        when(recommendationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecommendationLog saved = service.recordAlternative(entry(
                3L,
                " 888 ",
                " 999 ",
                " Soy Milk ",
                "  Brand  ",
                null));

        assertNotNull(saved);
        assertEquals("888", saved.getSourceBarcode());
        assertEquals("999", saved.getRecommendedBarcode());
        assertEquals("Soy Milk", saved.getRecommendedName());
        assertEquals("Brand", saved.getRecommendedBrand());
        assertEquals(RecommendationDataQuality.VERIFIED.name(), saved.getDataQuality());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void recordAlternativeSkipsInvalidEntriesAndBlankBrand() {
        assertNull(service.recordAlternative(entry(0L, "888", "999", "Name", "Brand", RecommendationDataQuality.PARTIAL)));
        verify(recommendationLogRepository, never()).save(any());

        when(recommendationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RecommendationLog saved = service.recordAlternative(entry(
                3L, "888", "999", "Name", "   ", RecommendationDataQuality.PARTIAL));
        assertEquals("PARTIAL", saved.getDataQuality());
        assertNull(saved.getRecommendedBrand());
    }

    @Test
    void recordAlternativeReturnsNullWhenPersistenceFails() {
        when(recommendationLogRepository.save(any()))
                .thenThrow(new DataRetrievalFailureException("db"));

        assertNull(service.recordAlternative(entry(
                3L, "888", "999", "Name", "Brand", RecommendationDataQuality.VERIFIED)));
    }

    @Test
    void recordAlternativesDedupesAndIgnoresNullBarcodes() {
        when(recommendationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordAlternatives(null);
        service.recordAlternatives(List.of());
        service.recordAlternatives(List.of(
                entry(3L, "888", null, "Name", "Brand", RecommendationDataQuality.VERIFIED),
                entry(3L, "888", "999", "First", "Brand", RecommendationDataQuality.VERIFIED),
                entry(3L, "888", " 999 ", "Second", "Brand", RecommendationDataQuality.VERIFIED)));

        verify(recommendationLogRepository).save(any());
    }

    @Test
    void recordDiscoveryAuditCapsLatencyAndSkipsInvalid() {
        assertNull(service.recordDiscoveryAudit(new RecommendationDiscoveryAudit(
                0L, 3L, "888", "gpt", 1, 2, 10L, "{}", 1, 0)));

        when(recommendationAiLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<RecommendationAiLog> captor = ArgumentCaptor.forClass(RecommendationAiLog.class);

        RecommendationAiLog saved = service.recordDiscoveryAudit(new RecommendationDiscoveryAudit(
                10L,
                3L,
                " 888 ",
                "  ",
                1,
                2,
                Integer.MAX_VALUE + 1L,
                "{}",
                -1,
                -2));

        verify(recommendationAiLogRepository).save(captor.capture());
        assertEquals(Integer.MAX_VALUE, captor.getValue().getLatencyMs());
        assertEquals(0, captor.getValue().getCandidatesAccepted());
        assertEquals(0, captor.getValue().getCandidatesRejected());
        assertNull(captor.getValue().getModelId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void recordDiscoveryAuditReturnsNullWhenPersistenceFails() {
        when(recommendationAiLogRepository.save(any()))
                .thenThrow(new DataRetrievalFailureException("db"));

        assertNull(service.recordDiscoveryAudit(new RecommendationDiscoveryAudit(
                10L, 3L, "888", "gpt", 1, 2, 5L, "{}", 1, 0)));
    }

    @Test
    void listHistoryForProfileRejectsMissingIds() {
        assertTrue(service.listHistoryForProfile(null).isEmpty());
        assertTrue(service.listHistoryForProfile(0L).isEmpty());
        verify(recommendationLogRepository, never())
                .findByProfileIdAndShownToUserTrueOrderByCreatedAtDesc(any());
    }

    private static RecommendationLogEntry entry(
            Long profileId,
            String sourceBarcode,
            String recommendedBarcode,
            String name,
            String brand,
            RecommendationDataQuality dataQuality) {
        return new RecommendationLogEntry(
                profileId,
                10L,
                sourceBarcode,
                recommendedBarcode,
                name,
                brand,
                RecommendationDiscoveryTier.TIER_A_CATALOG,
                BigDecimal.ONE,
                "category_match",
                dataQuality,
                true);
    }
}

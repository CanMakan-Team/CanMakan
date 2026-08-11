package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of UC17: groups {@link RecommendationLog} rows into scan sessions and
 * enriches them with source product and scan metadata for the history API.
 */
@Service
@RequiredArgsConstructor
public class RecommendationHistoryService {

    private static final String PLACEHOLDER_PRODUCT_NAME = "Unknown product";
    private static final DateTimeFormatter RECOMMENDED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RecommendationLogService recommendationLogService;
    private final ScanRepository scanRepository;
    private final CatalogProductRepository catalogProductRepository;

    @Transactional(readOnly = true)
    public RecommendationHistoryResponse getHistoryForProfile(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId must not be null");
        }

        List<RecommendationLog> logs = recommendationLogService.listHistoryForProfile(profileId);
        if (logs.isEmpty()) {
            return RecommendationHistoryResponse.empty(profileId);
        }

        Map<String, List<RecommendationLog>> groupedLogs = groupLogs(logs);
        Map<Long, Scan> scansById = loadScans(groupedLogs.values().stream()
                .flatMap(List::stream)
                .map(RecommendationLog::getScanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<String, CatalogProduct> productsByBarcode = loadSourceProducts(groupedLogs.values().stream()
                .flatMap(List::stream)
                .map(RecommendationLog::getSourceBarcode)
                .filter(barcode -> barcode != null && !barcode.isBlank())
                .collect(Collectors.toSet()));

        List<RecommendationHistoryEntryDto> history = groupedLogs.values().stream()
                .map(sessionLogs -> toEntry(sessionLogs, scansById, productsByBarcode))
                .sorted(Comparator.comparing(
                        RecommendationHistoryEntryDto::recommendedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new RecommendationHistoryResponse(profileId, history);
    }

    private static Map<String, List<RecommendationLog>> groupLogs(List<RecommendationLog> logs) {
        Map<String, List<RecommendationLog>> grouped = new LinkedHashMap<>();
        for (RecommendationLog log : logs) {
            grouped.computeIfAbsent(sessionKey(log), key -> new ArrayList<>()).add(log);
        }
        return grouped;
    }

    private static String sessionKey(RecommendationLog log) {
        if (log.getScanId() != null) {
            return "scan:" + log.getScanId();
        }
        LocalDateTime createdAt = log.getCreatedAt();
        String timestamp = createdAt == null
                ? "unknown"
                : createdAt.truncatedTo(ChronoUnit.SECONDS).format(RECOMMENDED_AT_FORMATTER);
        return "orphan:" + log.getSourceBarcode() + "|" + timestamp;
    }

    private Map<Long, Scan> loadScans(Set<Long> scanIds) {
        if (scanIds.isEmpty()) {
            return Map.of();
        }
        return scanRepository.findAllById(scanIds).stream()
                .collect(Collectors.toMap(Scan::getId, Function.identity()));
    }

    private Map<String, CatalogProduct> loadSourceProducts(Set<String> barcodes) {
        if (barcodes.isEmpty()) {
            return Map.of();
        }
        return catalogProductRepository.findAllById(barcodes).stream()
                .collect(Collectors.toMap(CatalogProduct::getBarcode, Function.identity()));
    }

    private RecommendationHistoryEntryDto toEntry(
            List<RecommendationLog> sessionLogs,
            Map<Long, Scan> scansById,
            Map<String, CatalogProduct> productsByBarcode) {
        RecommendationLog first = sessionLogs.getFirst();
        Scan scan = first.getScanId() == null ? null : scansById.get(first.getScanId());
        CatalogProduct sourceProduct = productsByBarcode.get(first.getSourceBarcode());

        String sourceProductName = sourceProduct != null && hasText(sourceProduct.getProductName())
                ? sourceProduct.getProductName()
                : PLACEHOLDER_PRODUCT_NAME;
        String sourceBrand = sourceProduct != null && sourceProduct.getBrand() != null
                ? sourceProduct.getBrand()
                : "";

        LocalDateTime recommendedAt = scan != null && scan.getScannedAt() != null
                ? scan.getScannedAt()
                : first.getCreatedAt();
        String sourceVerdict = scan != null ? scan.getVerdict() : null;

        List<RecommendationHistoryAlternativeDto> alternatives = sessionLogs.stream()
                .sorted(Comparator.comparing(
                        RecommendationLog::getRankScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAlternative)
                .toList();

        return new RecommendationHistoryEntryDto(
                first.getScanId(),
                first.getSourceBarcode(),
                sourceProductName,
                sourceBrand,
                sourceVerdict,
                formatTimestamp(recommendedAt),
                alternatives
        );
    }

    private RecommendationHistoryAlternativeDto toAlternative(RecommendationLog log) {
        return new RecommendationHistoryAlternativeDto(
                log.getRecommendedBarcode(),
                log.getRecommendedName(),
                log.getRecommendedBrand() == null ? "" : log.getRecommendedBrand(),
                log.getMatchReason(),
                log.getRankScore(),
                log.getDiscoveryTier()
        );
    }

    private static String formatTimestamp(LocalDateTime timestamp) {
        return timestamp == null
                ? null
                : timestamp.truncatedTo(ChronoUnit.SECONDS).format(RECOMMENDED_AT_FORMATTER);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

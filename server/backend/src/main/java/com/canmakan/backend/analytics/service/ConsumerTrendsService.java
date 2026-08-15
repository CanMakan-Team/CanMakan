package com.canmakan.backend.analytics.service;

import com.canmakan.backend.analytics.dto.CategoryScanTrend;
import com.canmakan.backend.analytics.dto.ConsumerTrendsAppliedFilters;
import com.canmakan.backend.analytics.dto.ConsumerTrendsDataQuality;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.DailyTrendPoint;
import com.canmakan.backend.analytics.dto.FlaggedIngredientTrend;
import com.canmakan.backend.analytics.dto.PeakScanDay;
import com.canmakan.backend.analytics.dto.ProductScanTrend;
import com.canmakan.backend.analytics.dto.RestrictionTrend;
import com.canmakan.backend.analytics.dto.TrendPeriod;
import com.canmakan.backend.analytics.dto.TrendSummary;
import com.canmakan.backend.analytics.exception.ConsumerTrendsValidationException;
import com.canmakan.backend.analytics.repository.CategoryScanOverviewProjection;
import com.canmakan.backend.analytics.repository.DailyScanTrendProjection;
import com.canmakan.backend.analytics.repository.ProductScanRankingProjection;
import com.canmakan.backend.analytics.repository.ScanAnalyticsRepository;
import com.canmakan.backend.analytics.repository.ScanFindingProjection;
import com.canmakan.backend.analytics.repository.ScanSummaryProjection;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds privacy-safe UC7 consumer trend aggregates from persisted scans. */
@Service
public class ConsumerTrendsService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Singapore");

    private static final String BUSINESS_TIMEZONE = BUSINESS_ZONE.getId();
    private static final String UNCATEGORISED = "Uncategorised";
    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 90;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_CATEGORY_LENGTH = 1000;
    private static final int RATIO_SCALE = 2;
    private static final int FIXED_RANKING_LIMIT = 20;
    private static final Comparator<String> DISPLAY_SPELLING_ORDER = Comparator.naturalOrder();
    private static final Pattern INTERNAL_WHITESPACE = Pattern.compile(
            "\\s+",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final TypeReference<List<Finding>> FINDING_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SENTINEL_INGREDIENTS = Set.of(
            Finding.SUBJECT_UNKNOWN,
            Finding.SUBJECT_LABEL,
            Finding.SUBJECT_NUTRITION,
            "incomplete",
            "missing data",
            "not available",
            "n/a",
            "null"
    );
    private static final Set<String> SENTINEL_RESTRICTION_CODES = Set.of(
            "INCOMPLETE_DATA",
            "UNRESOLVED",
            "UNRESOLVED_INGREDIENT",
            "UNAVAILABLE_NUTRITION",
            "INVALID_NUTRITION",
            "MODEL_EVIDENCE",
            "UNKNOWN",
            "N/A",
            "NULL"
    );

    private final ScanAnalyticsRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ConsumerTrendsService(
            ScanAnalyticsRepository repository,
            ObjectMapper objectMapper
    ) {
        this(repository, objectMapper, Clock.system(BUSINESS_ZONE));
    }

    ConsumerTrendsService(
            ScanAnalyticsRepository repository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Backward-compatible entry point for an unfiltered UC7 request. */
    @Transactional(readOnly = true)
    public ConsumerTrendsResponse generateTrends(LocalDate from, LocalDate to, Integer limit) {
        return generateTrends(from, to, limit, null);
    }

    /**
     * Generates aggregate consumer trends for an inclusive Singapore reporting period.
     * The ingredient limit does not alter the fixed top-20 product or restriction rankings.
     */
    @Transactional(readOnly = true)
    public ConsumerTrendsResponse generateTrends(
            LocalDate from,
            LocalDate to,
            Integer limit,
            String category
    ) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        ResolvedRequest request = resolveRequest(from, to, limit, category, today);
        Instant start = request.from().atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = request.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

        SummaryCounts counts = toSummaryCounts(
                repository.aggregateSummary(start, end, request.category())
        );
        List<DailyTrendPoint> dailyTrend = buildDailyTrend(
                request.from(),
                request.periodDays(),
                counts,
                repository.aggregateDailyTrend(start, end, request.category())
        );
        TrendSummary summary = buildSummary(counts, request.periodDays(), dailyTrend);
        FindingResult findingResult = aggregateFindings(
                repository.findFindingRows(start, end, request.category()),
                request.ingredientLimit()
        );

        return new ConsumerTrendsResponse(
                new TrendPeriod(request.from(), request.to(), BUSINESS_TIMEZONE),
                new ConsumerTrendsAppliedFilters(request.category()),
                summary,
                dailyTrend,
                buildProductRanking(
                        repository.rankProducts(start, end, request.category()),
                        counts.totalScans()
                ),
                buildCategoryOverview(repository.aggregateCategoryOverview(start, end)),
                findingResult.restrictions(),
                findingResult.ingredients(),
                new ConsumerTrendsDataQuality(
                        findingResult.skippedMalformedFindings() > 0,
                        findingResult.skippedMalformedFindings()
                ),
                OffsetDateTime.ofInstant(clock.instant(), BUSINESS_ZONE)
        );
    }

    private static ResolvedRequest resolveRequest(
            LocalDate from,
            LocalDate to,
            Integer limit,
            String category,
            LocalDate today
    ) {
        if ((from == null) != (to == null)) {
            throw new ConsumerTrendsValidationException("from and to must be supplied together");
        }

        LocalDate resolvedTo = to == null ? today : to;
        LocalDate resolvedFrom = from == null
                ? resolvedTo.minusDays(DEFAULT_PERIOD_DAYS - 1L)
                : from;

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ConsumerTrendsValidationException("from must not be after to");
        }
        if (resolvedTo.isAfter(today)) {
            throw new ConsumerTrendsValidationException("future reporting dates are not allowed");
        }

        long periodDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        if (periodDays > MAX_PERIOD_DAYS) {
            throw new ConsumerTrendsValidationException(
                    "reporting period must not exceed 90 days"
            );
        }

        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw new ConsumerTrendsValidationException("limit must be between 1 and 20");
        }

        return new ResolvedRequest(
                resolvedFrom,
                resolvedTo,
                (int) periodDays,
                resolvedLimit,
                normalizeRequestedCategory(category)
        );
    }

    private static String normalizeRequestedCategory(String category) {
        if (category == null) {
            return null;
        }
        String normalized = INTERNAL_WHITESPACE.matcher(category.strip()).replaceAll(" ");
        if (normalized.length() > MAX_CATEGORY_LENGTH) {
            throw new ConsumerTrendsValidationException(
                    "category must not exceed 1000 characters"
            );
        }
        if (normalized.isEmpty()
                || "0".equals(normalized)
                || UNCATEGORISED.equalsIgnoreCase(normalized)) {
            return UNCATEGORISED;
        }
        return normalized;
    }

    private static SummaryCounts toSummaryCounts(ScanSummaryProjection projection) {
        if (projection == null) {
            throw inconsistentAnalytics("summary is missing");
        }

        long total = projection.getTotalScans();
        long safe = projection.getSafeCount();
        long warning = projection.getWarningCount();
        long unsafe = projection.getUnsafeCount();
        long uniqueProducts = projection.getUniqueProducts();
        validateBreakdown(total, safe, warning, unsafe, "summary");
        if (uniqueProducts < 0 || uniqueProducts > total) {
            throw inconsistentAnalytics("summary contains an invalid unique-product count");
        }
        return new SummaryCounts(total, safe, warning, unsafe, uniqueProducts);
    }

    private static TrendSummary buildSummary(
            SummaryCounts counts,
            int periodDays,
            List<DailyTrendPoint> dailyTrend
    ) {
        BigDecimal average = BigDecimal.valueOf(counts.totalScans())
                .divide(BigDecimal.valueOf(periodDays), RATIO_SCALE, RoundingMode.HALF_UP);
        PeakScanDay peak = counts.totalScans() == 0
                ? null
                : dailyTrend.stream()
                        .max(Comparator
                                .comparingLong(DailyTrendPoint::totalCount)
                                .thenComparing(DailyTrendPoint::date))
                        .map(point -> new PeakScanDay(point.date(), point.totalCount()))
                        .orElseThrow();
        return new TrendSummary(
                counts.totalScans(),
                counts.safeCount(),
                counts.warningCount(),
                counts.unsafeCount(),
                counts.uniqueProducts(),
                average,
                peak
        );
    }

    private static List<DailyTrendPoint> buildDailyTrend(
            LocalDate from,
            int periodDays,
            SummaryCounts summary,
            List<DailyScanTrendProjection> projections
    ) {
        if (projections == null) {
            throw inconsistentAnalytics("daily trend is missing");
        }

        Map<Long, DailyScanTrendProjection> projectionByOffset = new HashMap<>();
        for (DailyScanTrendProjection projection : projections) {
            if (projection == null) {
                throw inconsistentAnalytics("daily trend contains a missing row");
            }

            long offset = projection.getDayOffset();
            if (offset < 0 || offset >= periodDays) {
                throw inconsistentAnalytics("daily trend contains an out-of-range date");
            }
            if (projectionByOffset.putIfAbsent(offset, projection) != null) {
                throw inconsistentAnalytics("daily trend contains a duplicate date");
            }
            validateBreakdown(
                    projection.getTotalCount(),
                    projection.getSafeCount(),
                    projection.getWarningCount(),
                    projection.getUnsafeCount(),
                    "daily trend"
            );
        }

        List<DailyTrendPoint> dailyTrend = new ArrayList<>(periodDays);
        long totalAcrossDays = 0;
        for (int offset = 0; offset < periodDays; offset++) {
            DailyScanTrendProjection projection = projectionByOffset.get((long) offset);
            DailyTrendPoint point = projection == null
                    ? new DailyTrendPoint(from.plusDays(offset), 0, 0, 0, 0)
                    : new DailyTrendPoint(
                            from.plusDays(offset),
                            projection.getTotalCount(),
                            projection.getSafeCount(),
                            projection.getWarningCount(),
                            projection.getUnsafeCount()
                    );
            try {
                totalAcrossDays = Math.addExact(totalAcrossDays, point.totalCount());
            } catch (ArithmeticException exception) {
                throw inconsistentAnalytics("daily total overflowed");
            }
            dailyTrend.add(point);
        }

        if (totalAcrossDays != summary.totalScans()) {
            throw inconsistentAnalytics("daily totals do not match the summary");
        }
        return List.copyOf(dailyTrend);
    }

    private static List<ProductScanTrend> buildProductRanking(
            List<ProductScanRankingProjection> projections,
            long filteredTotalScans
    ) {
        if (projections == null) {
            throw inconsistentAnalytics("product ranking is missing");
        }

        Set<String> barcodes = new HashSet<>();
        List<ProductScanRankingProjection> sorted = new ArrayList<>(projections);
        for (ProductScanRankingProjection projection : sorted) {
            if (projection == null
                    || projection.getBarcode() == null
                    || projection.getBarcode().isBlank()) {
                throw inconsistentAnalytics("product ranking identity is missing");
            }
            if (!barcodes.add(projection.getBarcode())) {
                throw inconsistentAnalytics("product ranking contains a duplicate barcode");
            }
            if (projection.getScanCount() <= 0) {
                throw inconsistentAnalytics("product ranking contains an invalid count");
            }
        }
        sorted.sort(Comparator
                .comparingLong(ProductScanRankingProjection::getScanCount)
                .reversed()
                .thenComparing(ProductScanRankingProjection::getBarcode));

        List<ProductScanTrend> products = new ArrayList<>();
        for (int index = 0; index < Math.min(sorted.size(), FIXED_RANKING_LIMIT); index++) {
            ProductScanRankingProjection projection = sorted.get(index);
            String productName = projection.getProductName();
            products.add(new ProductScanTrend(
                    index + 1,
                    productName == null || productName.isBlank()
                            ? "Unknown product"
                            : productName.strip(),
                    projection.getScanCount(),
                    percentage(projection.getScanCount(), filteredTotalScans)
            ));
        }
        return List.copyOf(products);
    }

    private static List<CategoryScanTrend> buildCategoryOverview(
            List<CategoryScanOverviewProjection> projections
    ) {
        if (projections == null) {
            throw inconsistentAnalytics("category overview is missing");
        }

        long periodTotal = 0;
        Set<String> categories = new HashSet<>();
        for (CategoryScanOverviewProjection projection : projections) {
            if (projection == null
                    || projection.getCategory() == null
                    || projection.getCategory().isBlank()) {
                throw inconsistentAnalytics("category overview identity is missing");
            }
            if (projection.getScanCount() <= 0) {
                throw inconsistentAnalytics("category overview contains an invalid count");
            }
            if (!categories.add(projection.getCategory())) {
                throw inconsistentAnalytics("category overview contains a duplicate category");
            }
            try {
                periodTotal = Math.addExact(periodTotal, projection.getScanCount());
            } catch (ArithmeticException exception) {
                throw inconsistentAnalytics("category overview total overflowed");
            }
        }

        long denominator = periodTotal;
        return projections.stream()
                .sorted(Comparator
                        .comparingLong(CategoryScanOverviewProjection::getScanCount)
                        .reversed()
                        .thenComparing(CategoryScanOverviewProjection::getCategory))
                .map(projection -> new CategoryScanTrend(
                        projection.getCategory(),
                        projection.getScanCount(),
                        percentage(projection.getScanCount(), denominator)
                ))
                .toList();
    }

    private FindingResult aggregateFindings(List<ScanFindingProjection> rows, int ingredientLimit) {
        if (rows == null) {
            throw inconsistentAnalytics("finding rows are missing");
        }

        Map<String, Set<Long>> scanIdsByIngredient = new HashMap<>();
        Map<String, Set<String>> observedSpellingsByIngredient = new HashMap<>();
        Map<String, Set<Long>> scanIdsByRestriction = new HashMap<>();
        long skippedMalformedFindings = 0;
        for (ScanFindingProjection row : rows) {
            if (row == null || row.getScanId() == null) {
                throw inconsistentAnalytics("finding row identity is missing");
            }

            List<Finding> findings;
            try {
                findings = parseFindings(row.getFindingsJson());
            } catch (JsonProcessingException exception) {
                skippedMalformedFindings++;
                continue;
            }
            if (findings == null) {
                skippedMalformedFindings++;
                continue;
            }

            Set<String> ingredientsInScan = new HashSet<>();
            Set<String> restrictionsInScan = new HashSet<>();
            for (Finding finding : findings) {
                if (finding == null) {
                    continue;
                }
                NormalizedIngredient ingredient = normalizeIngredientName(finding.ingredientName());
                if (ingredient != null) {
                    ingredientsInScan.add(ingredient.identity());
                    observedSpellingsByIngredient
                            .computeIfAbsent(ingredient.identity(), ignored -> new HashSet<>())
                            .add(ingredient.displayName());
                }
                String restriction = normalizeRestrictionCode(finding.restrictionCode());
                if (restriction != null) {
                    restrictionsInScan.add(restriction);
                }
            }

            for (String ingredient : ingredientsInScan) {
                scanIdsByIngredient
                        .computeIfAbsent(ingredient, ignored -> new HashSet<>())
                        .add(row.getScanId());
            }
            for (String restriction : restrictionsInScan) {
                scanIdsByRestriction
                        .computeIfAbsent(restriction, ignored -> new HashSet<>())
                        .add(row.getScanId());
            }
        }

        List<FlaggedIngredientTrend> rankedIngredients = scanIdsByIngredient.entrySet().stream()
                .map(entry -> new FlaggedIngredientTrend(
                        selectDisplayName(observedSpellingsByIngredient.get(entry.getKey())),
                        entry.getValue().size()
                ))
                .sorted(Comparator
                        .comparingLong(FlaggedIngredientTrend::flaggedCount)
                        .reversed()
                        .thenComparing(FlaggedIngredientTrend::ingredientName))
                .limit(ingredientLimit)
                .toList();
        List<RestrictionTrend> rankedRestrictions = scanIdsByRestriction.entrySet().stream()
                .map(entry -> new RestrictionTrend(entry.getKey(), entry.getValue().size()))
                .sorted(Comparator
                        .comparingLong(RestrictionTrend::flaggedCount)
                        .reversed()
                        .thenComparing(RestrictionTrend::restrictionCode))
                .limit(FIXED_RANKING_LIMIT)
                .toList();

        return new FindingResult(
                rankedIngredients,
                rankedRestrictions,
                skippedMalformedFindings
        );
    }

    private List<Finding> parseFindings(String findingsJson) throws JsonProcessingException {
        if (findingsJson == null || findingsJson.isBlank()) {
            return null;
        }
        return objectMapper.readValue(findingsJson, FINDING_LIST_TYPE);
    }

    private static NormalizedIngredient normalizeIngredientName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return null;
        }

        String displayName = INTERNAL_WHITESPACE.matcher(ingredientName.strip()).replaceAll(" ");
        String identity = displayName.toLowerCase(Locale.ROOT);
        if (identity.isEmpty() || SENTINEL_INGREDIENTS.contains(identity)) {
            return null;
        }
        return new NormalizedIngredient(identity, displayName);
    }

    private static String normalizeRestrictionCode(String restrictionCode) {
        if (restrictionCode == null || restrictionCode.isBlank()) {
            return null;
        }
        String identity = INTERNAL_WHITESPACE.matcher(restrictionCode.strip())
                .replaceAll("_")
                .toUpperCase(Locale.ROOT);
        if (identity.isEmpty() || SENTINEL_RESTRICTION_CODES.contains(identity)) {
            return null;
        }
        return identity;
    }

    private static String selectDisplayName(Set<String> observedSpellings) {
        if (observedSpellings == null || observedSpellings.isEmpty()) {
            throw inconsistentAnalytics("ingredient display spelling is missing");
        }
        return observedSpellings.stream().min(DISPLAY_SPELLING_ORDER).orElseThrow();
    }

    private static BigDecimal percentage(long count, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE);
        }
        if (count < 0 || denominator < 0 || count > denominator) {
            throw inconsistentAnalytics("percentage inputs are invalid");
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static void validateBreakdown(
            long total,
            long safe,
            long warning,
            long unsafe,
            String source
    ) {
        if (total < 0 || safe < 0 || warning < 0 || unsafe < 0) {
            throw inconsistentAnalytics(source + " contains a negative count");
        }

        long componentTotal;
        try {
            componentTotal = Math.addExact(Math.addExact(safe, warning), unsafe);
        } catch (ArithmeticException exception) {
            throw inconsistentAnalytics(source + " count overflowed");
        }
        if (total != componentTotal) {
            throw inconsistentAnalytics(source + " count breakdown does not match its total");
        }
    }

    private static IllegalStateException inconsistentAnalytics(String detail) {
        return new IllegalStateException("Consumer trend analytics are inconsistent: " + detail);
    }

    private record ResolvedRequest(
            LocalDate from,
            LocalDate to,
            int periodDays,
            int ingredientLimit,
            String category
    ) {
    }

    private record SummaryCounts(
            long totalScans,
            long safeCount,
            long warningCount,
            long unsafeCount,
            long uniqueProducts
    ) {
    }

    private record FindingResult(
            List<FlaggedIngredientTrend> ingredients,
            List<RestrictionTrend> restrictions,
            long skippedMalformedFindings
    ) {
    }

    private record NormalizedIngredient(String identity, String displayName) {
    }
}

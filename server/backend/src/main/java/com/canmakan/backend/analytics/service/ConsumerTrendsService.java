package com.canmakan.backend.analytics.service;

import com.canmakan.backend.analytics.dto.ConsumerTrendsDataQuality;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.DailyTrendPoint;
import com.canmakan.backend.analytics.dto.FlaggedIngredientTrend;
import com.canmakan.backend.analytics.dto.TrendPeriod;
import com.canmakan.backend.analytics.dto.TrendSummary;
import com.canmakan.backend.analytics.repository.DailyScanTrendProjection;
import com.canmakan.backend.analytics.repository.ScanAnalyticsRepository;
import com.canmakan.backend.analytics.repository.ScanFindingProjection;
import com.canmakan.backend.analytics.repository.ScanSummaryProjection;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 90;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
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

    /**
     * Generates aggregate consumer trends for an inclusive Singapore reporting period.
     *
     * @param from first reporting date, or {@code null} together with {@code to}
     * @param to last reporting date, or {@code null} together with {@code from}
     * @param limit maximum ranked ingredients, or {@code null} for ten
     * @return the aggregate trend response
     */
    @Transactional(readOnly = true)
    public ConsumerTrendsResponse generateTrends(LocalDate from, LocalDate to, Integer limit) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        ResolvedRequest request = resolveRequest(from, to, limit, today);
        Instant start = request.from().atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = request.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

        TrendSummary summary = toSummary(repository.aggregateSummary(start, end));
        List<DailyTrendPoint> dailyTrend = buildDailyTrend(
                request.from(),
                request.periodDays(),
                summary,
                repository.aggregateDailyTrend(start, end)
        );
        IngredientResult ingredientResult = aggregateIngredients(
                repository.findFindingRows(start, end),
                request.limit()
        );

        return new ConsumerTrendsResponse(
                new TrendPeriod(request.from(), request.to(), BUSINESS_TIMEZONE),
                summary,
                dailyTrend,
                ingredientResult.ingredients(),
                new ConsumerTrendsDataQuality(
                        ingredientResult.skippedMalformedFindings() > 0,
                        ingredientResult.skippedMalformedFindings()
                ),
                OffsetDateTime.ofInstant(clock.instant(), BUSINESS_ZONE)
        );
    }

    private static ResolvedRequest resolveRequest(
            LocalDate from,
            LocalDate to,
            Integer limit,
            LocalDate today
    ) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("from and to must be supplied together");
        }

        LocalDate resolvedTo = to == null ? today : to;
        LocalDate resolvedFrom = from == null
                ? resolvedTo.minusDays(DEFAULT_PERIOD_DAYS - 1L)
                : from;

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (resolvedTo.isAfter(today)) {
            throw new IllegalArgumentException("future reporting dates are not allowed");
        }

        long periodDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        if (periodDays > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("reporting period must not exceed 90 days");
        }

        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 20");
        }

        return new ResolvedRequest(resolvedFrom, resolvedTo, (int) periodDays, resolvedLimit);
    }

    private static TrendSummary toSummary(ScanSummaryProjection projection) {
        if (projection == null) {
            throw inconsistentAnalytics("summary is missing");
        }

        long total = projection.getTotalScans();
        long safe = projection.getSafeCount();
        long warning = projection.getWarningCount();
        long unsafe = projection.getUnsafeCount();
        validateBreakdown(total, safe, warning, unsafe, "summary");
        return new TrendSummary(total, safe, warning, unsafe);
    }

    private static List<DailyTrendPoint> buildDailyTrend(
            LocalDate from,
            int periodDays,
            TrendSummary summary,
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

    private IngredientResult aggregateIngredients(List<ScanFindingProjection> rows, int limit) {
        if (rows == null) {
            throw inconsistentAnalytics("finding rows are missing");
        }

        Map<String, Set<Long>> scanIdsByIngredient = new HashMap<>();
        Map<String, Set<String>> observedSpellingsByIngredient = new HashMap<>();
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
            }

            for (String ingredient : ingredientsInScan) {
                scanIdsByIngredient
                        .computeIfAbsent(ingredient, ignored -> new HashSet<>())
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
                .limit(limit)
                .toList();

        return new IngredientResult(rankedIngredients, skippedMalformedFindings);
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

    private static String selectDisplayName(Set<String> observedSpellings) {
        if (observedSpellings == null || observedSpellings.isEmpty()) {
            throw inconsistentAnalytics("ingredient display spelling is missing");
        }
        return observedSpellings.stream().min(DISPLAY_SPELLING_ORDER).orElseThrow();
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

    private record ResolvedRequest(LocalDate from, LocalDate to, int periodDays, int limit) {
    }

    private record IngredientResult(
            List<FlaggedIngredientTrend> ingredients,
            long skippedMalformedFindings
    ) {
    }

    private record NormalizedIngredient(String identity, String displayName) {
    }
}

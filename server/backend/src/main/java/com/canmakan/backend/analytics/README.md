# analytics

Aggregated insights and reporting. HTTP is on [`AdminController`](../admin/AdminController.java), not in this package.

## Purpose
Produces anonymised consumer trends and application usage statistics.

## Responsibilities
- Consumer trends (scan activity, products, categories, restrictions and flagged ingredients)
- Application usage statistics
- Anonymised data export is a separate UC22 client responsibility and is not part of UC7

Family verdict trends (UC14) are aggregated on the web from `GET /api/families/me/scans`, not this package.
AI execution logs are owned by [`ai/log`](../ai/README.md) and surfaced on admin system health, not here.

## Key files

| File | Role |
| --- | --- |
| [`service/ConsumerTrendsService.java`](service/ConsumerTrendsService.java) | UC7 aggregates |
| [`service/UsageStatisticsService.java`](service/UsageStatisticsService.java) | UC15 usage |
| [`repository/ScanAnalyticsRepository.java`](repository/ScanAnalyticsRepository.java) | Scan projections |

## Note
All data used here should be anonymised where required.

## UC7 backend contract

`GET /api/admin/consumer-trends` is ADMIN-only and returns aggregate data. It accepts:

- `from` and `to` together as an inclusive Singapore calendar-date range (1-90 days);
- `limit` from 1-20 for `topFlaggedIngredients` only (default 10);
- optional `category`, matched case-insensitively after trimming/collapsing whitespace.

If dates are omitted, the period is the latest 30 inclusive Singapore dates. Database category
values that are null, blank or literal `0`, plus scans with a missing product or null barcode, are
reported as `Uncategorised`. A blank or literal `0` category filter selects `Uncategorised`.

The additive JSON response contains:

- `period` and `appliedFilters.category` (`null` means all categories);
- `summary`: existing verdict totals plus distinct non-null barcodes, two-decimal `HALF_UP`
  average scans per inclusive calendar day, and the most recent date when peak totals tie;
  `peakScanDay` is `null` when the filtered period has no scans;
- zero-filled `dailyTrend`;
- up to 20 `mostScannedProducts`, grouped/tied by barcode but exposing only rank, display name,
  scan count and percentage of all filtered scans;
- period-wide `categoryOverview`, intentionally unaffected by the category filter;
- up to 20 `topRestrictions`, counted once per normalized canonical restriction code per scan;
- `topFlaggedIngredients` with the existing requested-limit and distinct-scan semantics;
- `dataQuality` and `generatedAt`.

Product and category percentages use two-decimal `HALF_UP` rounding. Product percentages divide by
all filtered scans, including null-barcode scans. Category percentages divide by all eligible scans
in the unfiltered selected period, including `Uncategorised`. Product ties use barcode ascending;
category, restriction and ingredient ties use their exposed name/code ascending. Null-barcode scans
remain in scan, daily, verdict, category and finding aggregates, but are excluded from distinct
products and product ranking.

The response must never expose scan/user/profile/family identifiers, email, barcode, raw findings,
or individual scan rows. UC7 contains no report preview, print/PDF, CSV or other export behavior.

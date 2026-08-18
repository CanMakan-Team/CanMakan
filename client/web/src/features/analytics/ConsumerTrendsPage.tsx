import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { EmptyState, ErrorState, LoadingState } from "../../shared/ui/PageState";
import { consumerTrendsApiService } from "./consumerTrendsApiService";
import {
  chartEndLabelIndexes,
  consumerTrendsChartAxis,
} from "./consumerTrendsChartAxis";
import {
  PERIOD_OPTIONS,
  buildPeriodQuery,
  describeRangeError,
  matchingPresetDays,
  singaporeToday,
} from "./consumerTrendsDateRange";
import { downloadConsumerTrendsReport } from "./consumerTrendsReport";
import type {
  CategoryScanTrend,
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
  ProductScanTrend,
} from "./consumerTrendsTypes";

const ROWS_PER_PAGE = 10;
const INGREDIENT_RANKING_LIMIT = 20;

function isCalendarDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/u.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day;
}

function prepareConsumerTrendsResponse(response: ConsumerTrendsResponse): ConsumerTrendsResponse {
  const incomplete = () => new Error("The consumer trends data is incomplete. Please refresh and try again.");
  if (!response
    || !response.period
    || !isCalendarDate(response.period.from)
    || !isCalendarDate(response.period.to)
    || !response.summary) {
    throw incomplete();
  }

  const summaryValues = [
    response.summary.totalScans,
    response.summary.safeCount,
    response.summary.warningCount,
    response.summary.unsafeCount,
    response.summary.uniqueProducts,
    response.summary.averageScansPerDay,
  ];
  if (summaryValues.some((value) => !Number.isFinite(value))
    || (response.summary.peakScanDay !== null
      && (!isCalendarDate(response.summary.peakScanDay?.date)
        || !Number.isFinite(response.summary.peakScanDay?.scanCount)))) {
    throw incomplete();
  }

  const dailyTrend = Array.isArray(response.dailyTrend) ? response.dailyTrend : [];
  const products = Array.isArray(response.mostScannedProducts) ? response.mostScannedProducts : [];
  const categories = Array.isArray(response.categoryOverview) ? response.categoryOverview : [];
  const restrictions = Array.isArray(response.topRestrictions) ? response.topRestrictions : [];
  const ingredients = Array.isArray(response.topFlaggedIngredients) ? response.topFlaggedIngredients : [];
  if (dailyTrend.some((item) => !isCalendarDate(item?.date)
      || [item?.totalCount, item?.safeCount, item?.warningCount, item?.unsafeCount]
        .some((value) => !Number.isFinite(value)))
    || products.some((item) => typeof item?.productName !== "string"
      || [item?.rank, item?.scanCount, item?.percentage].some((value) => !Number.isFinite(value)))
    || categories.some((item) => typeof item?.category !== "string"
      || [item?.scanCount, item?.percentage].some((value) => !Number.isFinite(value)))
    || restrictions.some((item) => typeof item?.restrictionCode !== "string"
      || !Number.isFinite(item?.flaggedCount))
    || ingredients.some((item) => typeof item?.ingredientName !== "string"
      || !Number.isFinite(item?.flaggedCount))
    || (response.appliedFilters !== null
      && response.appliedFilters !== undefined
      && response.appliedFilters.category !== null
      && typeof response.appliedFilters.category !== "string")
    || (response.dataQuality !== null
      && response.dataQuality !== undefined
      && (typeof response.dataQuality.partial !== "boolean"
        || !Number.isFinite(response.dataQuality.skippedMalformedFindings)))) {
    throw incomplete();
  }

  return {
    ...response,
    appliedFilters: response.appliedFilters ?? { category: null },
    dailyTrend,
    mostScannedProducts: products,
    categoryOverview: categories,
    topRestrictions: restrictions,
    topFlaggedIngredients: ingredients,
    dataQuality: response.dataQuality ?? { partial: false, skippedMalformedFindings: 0 },
  };
}

function formatDate(date: string): string {
  return new Intl.DateTimeFormat("en-SG", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Singapore",
  }).format(new Date(`${date}T00:00:00+08:00`));
}

function formatShortDate(date: string): string {
  return new Intl.DateTimeFormat("en-SG", {
    day: "numeric",
    month: "short",
    timeZone: "Asia/Singapore",
  }).format(new Date(`${date}T00:00:00+08:00`));
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("en-SG").format(value);
}

export function ConsumerTrendsPage() {
  const today = singaporeToday();
  const [query, setQuery] = useState<ConsumerTrendsQuery>(() => buildPeriodQuery(30));
  const [fromInput, setFromInput] = useState(() => query.from ?? "");
  const [toInput, setToInput] = useState(() => query.to ?? "");
  const [rangeError, setRangeError] = useState<string | null>(null);
  const [data, setData] = useState<ConsumerTrendsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [exporting, setExporting] = useState(false);
  const exportInProgress = useRef(false);
  const latestLoadRequest = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++latestLoadRequest.current;
    setLoading(true);
    setError(null);
    setExportError(null);
    setExportSuccess(false);

    try {
      const response = await consumerTrendsApiService.getConsumerTrends({
        ...query,
        limit: INGREDIENT_RANKING_LIMIT,
      });
      if (requestId === latestLoadRequest.current) {
        setData(prepareConsumerTrendsResponse(response));
      }
    } catch (caught) {
      if (requestId === latestLoadRequest.current) {
        setError(caught instanceof Error ? caught.message : "Unable to load consumer trends.");
      }
    } finally {
      if (requestId === latestLoadRequest.current) {
        setLoading(false);
      }
    }
  }, [query]);

  useEffect(() => {
    const request = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(request);
  }, [load]);

  const updatePeriod = (value: string) => {
    if (value === "custom") return;
    const days = Number(value) as (typeof PERIOD_OPTIONS)[number];
    if (!PERIOD_OPTIONS.includes(days)) return;
    const nextQuery = buildPeriodQuery(days, query.category);
    setRangeError(null);
    setFromInput(nextQuery.from ?? "");
    setToInput(nextQuery.to ?? "");
    setQuery(nextQuery);
  };

  const applyCustomRange = (from: string, to: string) => {
    const message = describeRangeError(from, to, today);
    if (message) {
      setRangeError(message);
      return;
    }
    setRangeError(null);
    setQuery({
      from,
      to,
      category: query.category,
    });
  };

  const updateCategory = (category: string) => {
    setQuery({
      ...query,
      category: category || undefined,
    });
  };

  const presetDays = matchingPresetDays(query.from, query.to, today);

  const categoryOptions = data?.categoryOverview.map((item) => item.category) ?? [];
  const selectedCategory = query.category ?? "";
  const dataMatchesCurrentQuery = data !== null
    && data.period.from === query.from
    && data.period.to === query.to
    && (data.appliedFilters.category ?? "") === selectedCategory;
  const canExport = data !== null
    && dataMatchesCurrentQuery
    && data.summary.totalScans > 0
    && !loading
    && !error
    && !exporting;

  const generateReport = async () => {
    if (!data || !canExport || exportInProgress.current) return;

    exportInProgress.current = true;
    setExporting(true);
    setExportError(null);
    setExportSuccess(false);
    try {
      await downloadConsumerTrendsReport(data);
      setExportSuccess(true);
    } catch {
      setExportError("The report could not be downloaded. No file was saved. Please try again.");
    } finally {
      exportInProgress.current = false;
      setExporting(false);
    }
  };

  return (
    <div className="admin-page analytics-page">
      <header className="page-header page-header--split analytics-header">
        <div>
          <p className="eyebrow">ADMIN ANALYTICS</p>
          <h1>Consumer Trends</h1>
          <p>
            Aggregated scan activity and dietary-concern insights. Scan activity indicates consumer interest,
            not actual sales.
          </p>
        </div>

        <div className="analytics-toolbar">
          <div className="analytics-controls" aria-label="Consumer trends filters">
            <label>
              Period
              <select
                value={presetDays ?? "custom"}
                onChange={(event) => updatePeriod(event.target.value)}
                disabled={loading}
              >
                {PERIOD_OPTIONS.map((days) => (
                  <option key={days} value={days}>
                    Last {days} Days
                  </option>
                ))}
                <option value="custom">Custom range</option>
              </select>
            </label>

            <label>
              From
              <input
                type="date"
                value={fromInput}
                max={today}
                disabled={loading}
                onChange={(event) => {
                  const nextFrom = event.target.value;
                  setFromInput(nextFrom);
                  applyCustomRange(nextFrom, toInput);
                }}
              />
            </label>

            <label>
              To
              <input
                type="date"
                value={toInput}
                max={today}
                disabled={loading}
                onChange={(event) => {
                  const nextTo = event.target.value;
                  setToInput(nextTo);
                  applyCustomRange(fromInput, nextTo);
                }}
              />
            </label>

            <label>
              Product Category
              <select
                value={selectedCategory}
                onChange={(event) => updateCategory(event.target.value)}
                disabled={loading}
              >
                <option value="">All Categories</option>
                {categoryOptions.map((category) => (
                  <option key={category} value={category}>
                    {category}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="analytics-toolbar-actions">
            <button
              type="button"
              className="button button--secondary"
              onClick={() => void load()}
              disabled={loading}
            >
              {loading ? "Refreshing…" : "Refresh"}
            </button>
            <button
              type="button"
              className="button button--primary"
              onClick={() => void generateReport()}
              disabled={!canExport}
              aria-describedby="consumer-trends-export-help"
            >
              {exporting ? "Generating…" : "Generate Report"}
            </button>
          </div>
        </div>
      </header>

      <p id="consumer-trends-export-help" className="analytics-export-help">
        Exports the currently loaded anonymous aggregate data only. Raw scans and personal information are excluded.
      </p>

      {rangeError ? <p className="form-message form-message--error" role="alert">{rangeError}</p> : null}
      {exportError ? <p className="form-message form-message--error" role="alert">{exportError}</p> : null}
      {exportSuccess ? (
        <p className="form-message form-message--success" role="status">Consumer trends report downloaded.</p>
      ) : null}

      {loading && !data ? <LoadingState label="Loading consumer trends…" /> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {!error && data ? (
        <ConsumerTrendsResult
          data={data}
          selectedCategory={selectedCategory}
          onCategoryChange={updateCategory}
        />
      ) : null}
    </div>
  );
}

function ConsumerTrendsResult({
  data,
  selectedCategory,
  onCategoryChange,
}: {
  data: ConsumerTrendsResponse;
  selectedCategory: string;
  onCategoryChange: (category: string) => void;
}) {
  const noActivity = data.summary.totalScans === 0;
  const listResetKey = `${data.period.from}|${data.period.to}|${data.appliedFilters.category ?? ""}`;
  const periodLabel = `${formatDate(data.period.from)} – ${formatDate(data.period.to)}`;

  return (
    <>
      <section className="analytics-summary-grid" aria-label="Consumer trends summary">
        <SummaryCard label="Total Scans" value={formatNumber(data.summary.totalScans)} />
        <SummaryCard label="Unique Products Scanned" value={formatNumber(data.summary.uniqueProducts)} />
        <SummaryCard label="Average Scans per Day" value={data.summary.averageScansPerDay.toFixed(2)} />
        <SummaryCard
          label="Peak Scan Day"
          value={data.summary.peakScanDay ? formatDate(data.summary.peakScanDay.date) : "No activity"}
          detail={data.summary.peakScanDay ? `${formatNumber(data.summary.peakScanDay.scanCount)} scans` : undefined}
        />
      </section>

      {data.dataQuality.partial ? (
        <div className="analytics-warning" role="status">
          <strong>Partial dietary-concern data:</strong> {formatNumber(data.dataQuality.skippedMalformedFindings)} scan finding records could not be read.
        </div>
      ) : null}

      {noActivity ? (
        <EmptyState
          title="No scan activity in this period"
          description="Try another period or category. The charts remain visible with zero values for the requested dates."
          showMascot={false}
        />
      ) : null}

      <DailyActivityChart daily={data.dailyTrend} />
      <OutcomeMix data={data} />

      <div className="analytics-two-column">
        <ProductRankingChart
          products={data.mostScannedProducts}
          resetKey={listResetKey}
          periodLabel={periodLabel}
        />
        <CategoryOverviewChart
          categories={data.categoryOverview}
          selectedCategory={selectedCategory}
          onCategoryChange={onCategoryChange}
          resetKey={listResetKey}
          periodLabel={periodLabel}
        />
      </div>

      <div className="analytics-two-column">
        <ConcernBars
          eyebrow="Dietary concerns"
          title="Most Frequently Triggered Dietary Restrictions"
          description="Counts show scan-triggered dietary-concern signals, not population prevalence."
          items={data.topRestrictions.map((item) => ({ label: item.restrictionCode, count: item.flaggedCount }))}
          emptyMessage="No dietary restrictions were triggered in the selected period."
          paginationLabel="Dietary restriction ranking pages"
          resetKey={listResetKey}
          periodLabel={periodLabel}
        />
        <ConcernBars
          eyebrow="Ingredient flags"
          title="Top Flagged Ingredients"
          description="Counts show ingredients flagged in scan findings, not population prevalence."
          items={data.topFlaggedIngredients.map((item) => ({ label: item.ingredientName, count: item.flaggedCount }))}
          emptyMessage="No ingredients were flagged in the selected period."
          paginationLabel="Flagged ingredient ranking pages"
          resetKey={listResetKey}
          periodLabel={periodLabel}
        />
      </div>
    </>
  );
}

function SummaryCard({ label, value, detail }: { label: string; value: ReactNode; detail?: string }) {
  return (
    <article className="analytics-card summary-card">
      <p className="analytics-label">{label}</p>
      <strong>{value}</strong>
      {detail ? <span>{detail}</span> : null}
    </article>
  );
}

function DailyActivityChart({ daily }: { daily: ConsumerTrendsResponse["dailyTrend"] }) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)
  const width = 720
  const height = 220
  const padLeft = 48
  const padRight = 16
  const padTop = 12
  const padBottom = 32
  const plotWidth = width - padLeft - padRight
  const plotHeight = height - padTop - padBottom
  const dataMax = Math.max(0, ...daily.map((item) => item.totalCount))
  const { axisMax, ticks } = consumerTrendsChartAxis(dataMax)
  const baseline = height - padBottom
  const pointFor = (index: number, value: number) => {
    const x = daily.length <= 1 ? padLeft + plotWidth / 2 : padLeft + (index / (daily.length - 1)) * plotWidth
    const y = baseline - (value / axisMax) * plotHeight
    return { x, y }
  }
  const linePoints = daily.map((item, index) => {
    const point = pointFor(index, item.totalCount)
    return `${point.x},${point.y}`
  })
  const areaPoints = daily.length > 0
    ? `${pointFor(0, 0).x},${baseline} ${linePoints.join(" ")} ${pointFor(daily.length - 1, 0).x},${baseline}`
    : ""
  const xLabelIndexes = chartEndLabelIndexes(daily.length)
  const hovered = hoveredIndex === null ? undefined : daily[hoveredIndex]
  const hoveredPoint = hovered && hoveredIndex !== null
    ? pointFor(hoveredIndex, hovered.totalCount)
    : null
  const dailyResetKey = `${daily[0]?.date ?? ""}|${daily[daily.length - 1]?.date ?? ""}|${daily.length}`
  const {
    page: dailyPage,
    setPage: setDailyPage,
    start: dailyStart,
    visible: visibleDays,
    rangeEnd: dailyRangeEnd,
    total: dailyTotal,
  } = usePagedItems(daily, dailyResetKey)

  return (
    <section className="analytics-panel analytics-line-panel" aria-labelledby="daily-activity-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">SCAN ACTIVITY</p>
          <h2 id="daily-activity-title">Daily Scan Activity</h2>
        </div>
        <span>{daily.length} calendar days</span>
      </div>

      <div className="analytics-line-chart-wrap">
        <svg
          className="analytics-line-chart"
          viewBox={`0 0 ${width} ${height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label="Line chart of total scans for every day in the selected period"
        >
          {ticks.map((tick) => {
            const y = pointFor(0, tick).y
            return (
              <g key={tick}>
                <line x1={padLeft} y1={y} x2={width - padRight} y2={y} className="chart-grid-line" />
                <text x={padLeft - 8} y={y + 3} textAnchor="end" className="chart-axis-label">
                  {tick}
                </text>
              </g>
            )
          })}
          {areaPoints ? <polygon points={areaPoints} className="chart-area" /> : null}
          {linePoints.length > 1 ? <polyline points={linePoints.join(" ")} className="chart-line" /> : null}
          {daily.map((item, index) => {
            if (item.totalCount <= 0) return null
            const point = pointFor(index, item.totalCount)
            return (
              <g key={item.date}>
                <circle
                  cx={point.x}
                  cy={point.y}
                  r="9"
                  className="chart-point-hit"
                  onMouseEnter={() => setHoveredIndex(index)}
                  onMouseLeave={() => setHoveredIndex(null)}
                />
                <circle cx={point.x} cy={point.y} r="2" className="chart-point" />
              </g>
            )
          })}
          {xLabelIndexes.map((index) => {
            const item = daily[index]
            const x = pointFor(index, item.totalCount).x
            const textAnchor = index === 0 ? "start" : index === daily.length - 1 ? "end" : "middle"
            return (
              <text key={item.date} x={x} y={height - 8} textAnchor={textAnchor} className="chart-axis-label">
                {formatShortDate(item.date)}
              </text>
            )
          })}
        </svg>
        {hovered && hoveredPoint ? (
          <div
            className="chart-tooltip"
            role="tooltip"
            style={{
              left: `${(hoveredPoint.x / width) * 100}%`,
              top: `${(hoveredPoint.y / height) * 100}%`,
            }}
          >
            <strong>{formatDate(hovered.date)}</strong>
            <span>{hovered.totalCount} scans</span>
            <span>
              {hovered.safeCount} safe · {hovered.warningCount} warning · {hovered.unsafeCount} unsafe
            </span>
          </div>
        ) : null}
      </div>

      <details className="analytics-data-table">
        <summary>View daily values</summary>
        <div className="analytics-data-table-toolbar">
          <ListPageNav
            label="Daily values pages"
            page={dailyPage}
            total={dailyTotal}
            start={dailyStart}
            rangeEnd={dailyRangeEnd}
            onPageChange={setDailyPage}
          />
        </div>
        <div className="table-scroll">
          <table aria-label="Daily scan counts for the selected period">
            <colgroup>
              <col className="daily-col-date" />
              <col className="daily-col-metric" span={4} />
            </colgroup>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Total</th>
                <th scope="col">Safe</th>
                <th scope="col">Warning</th>
                <th scope="col">Unsafe</th>
              </tr>
            </thead>
            <tbody>
              {visibleDays.map((item) => (
                <tr key={item.date}>
                  <th scope="row">{formatDate(item.date)}</th>
                  <td>{item.totalCount}</td>
                  <td>{item.safeCount}</td>
                  <td>{item.warningCount}</td>
                  <td>{item.unsafeCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}

function usePagedItems<T>(items: T[], resetKey: string) {
  const [page, setPage] = useState(0);
  useEffect(() => {
    setPage(0);
  }, [resetKey]);

  const totalPages = Math.max(1, Math.ceil(items.length / ROWS_PER_PAGE));
  const safePage = Math.min(page, totalPages - 1);
  const start = safePage * ROWS_PER_PAGE;
  return {
    page: safePage,
    setPage,
    start,
    visible: items.slice(start, start + ROWS_PER_PAGE),
    rangeEnd: Math.min(start + ROWS_PER_PAGE, items.length),
    total: items.length,
  };
}

function ListPageNav({
  label,
  page,
  total,
  start,
  rangeEnd,
  onPageChange,
}: {
  label: string;
  page: number;
  total: number;
  start: number;
  rangeEnd: number;
  onPageChange: (page: number) => void;
}) {
  if (total === 0) return null;
  const totalPages = Math.max(1, Math.ceil(total / ROWS_PER_PAGE));
  const rangeText = `${start + 1}–${rangeEnd} of ${total}`;
  if (total <= ROWS_PER_PAGE) return <span>{rangeText}</span>;
  return (
    <nav className="analytics-pagination analytics-pagination--inline" aria-label={label}>
      <span>{rangeText}</span>
      <button
        type="button"
        className="button button--secondary"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </button>
      <button
        type="button"
        className="button button--secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </nav>
  );
}

function ProductRankingChart({
  products,
  resetKey,
  periodLabel,
}: {
  products: ProductScanTrend[];
  resetKey: string;
  periodLabel: string;
}) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(products, resetKey);
  const maxCount = Math.max(1, ...products.map((item) => item.scanCount));

  return (
    <section className="analytics-panel" aria-labelledby="products-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">Product interest</p>
          <h2 id="products-title">Most Scanned Products</h2>
        </div>
        <div className="analytics-panel-heading-meta">
          <span>{periodLabel}</span>
          <ListPageNav
            label="Product ranking pages"
            page={page}
            total={total}
            start={start}
            rangeEnd={rangeEnd}
            onPageChange={setPage}
          />
        </div>
      </div>

      {visible.length ? (
        <ol className="horizontal-bar-list product-bar-list" start={start + 1}>
          {visible.map((product) => (
            <li key={`${product.rank}-${product.productName}`}>
              <div className="horizontal-bar-label">
                <span title={product.productName}>{product.productName}</span>
                <strong>{formatNumber(product.scanCount)} scans · {product.percentage.toFixed(1)}%</strong>
              </div>
              <div className="horizontal-bar-track" aria-hidden="true">
                <span
                  data-testid={`product-bar-${product.rank}`}
                  style={{ width: `${(product.scanCount / maxCount) * 100}%` }}
                />
              </div>
            </li>
          ))}
        </ol>
      ) : (
        <p className="empty-copy">No products were resolved for this period.</p>
      )}

      <p className="analytics-note">
        Percentages use all filtered scans as the denominator, including scans without a resolved product barcode.
      </p>
    </section>
  );
}

function CategoryOverviewChart({
  categories,
  selectedCategory,
  onCategoryChange,
  resetKey,
  periodLabel,
}: {
  categories: CategoryScanTrend[];
  selectedCategory: string;
  onCategoryChange: (category: string) => void;
  resetKey: string;
  periodLabel: string;
}) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(categories, resetKey);
  const maxCount = Math.max(1, ...categories.map((item) => item.scanCount));

  return (
    <section className="analytics-panel" aria-labelledby="categories-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">Category mix</p>
          <h2 id="categories-title">Scan Activity by Category</h2>
        </div>
        <div className="analytics-panel-heading-meta">
          <span>{periodLabel}</span>
          {selectedCategory ? (
            <button type="button" className="text-button" onClick={() => onCategoryChange("")}>
              Show all
            </button>
          ) : null}
          <ListPageNav
            label="Category ranking pages"
            page={page}
            total={total}
            start={start}
            rangeEnd={rangeEnd}
            onPageChange={setPage}
          />
        </div>
      </div>

      {visible.length ? (
        <div className="category-bar-list">
          {visible.map((item) => {
            const selected = selectedCategory === item.category;
            return (
              <button
                key={item.category}
                type="button"
                className={`category-bar-row${selected ? " is-selected" : ""}`}
                aria-pressed={selected}
                onClick={() => onCategoryChange(selected ? "" : item.category)}
              >
                <span className="horizontal-bar-label">
                  <span>{item.category}</span>
                  <strong>{formatNumber(item.scanCount)} scans · {item.percentage.toFixed(1)}%</strong>
                </span>
                <span className="horizontal-bar-track" aria-hidden="true">
                  <span style={{ width: `${(item.scanCount / maxCount) * 100}%` }} />
                </span>
              </button>
            );
          })}
        </div>
      ) : (
        <p className="empty-copy">No category activity is available for this period.</p>
      )}
      <p className="analytics-note">
        This mix uses the selected dates. Choosing a category still leaves this mix unchanged and applies the filter to the other charts.
      </p>
    </section>
  );
}

function ConcernBars({
  eyebrow,
  title,
  description,
  items,
  emptyMessage,
  paginationLabel,
  resetKey,
  periodLabel,
}: {
  eyebrow: string;
  title: string;
  description: string;
  items: Array<{ label: string; count: number }>;
  emptyMessage: string;
  paginationLabel: string;
  resetKey: string;
  periodLabel: string;
}) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(items, resetKey);
  const maxCount = Math.max(1, ...items.map((item) => item.count));

  return (
    <section className="analytics-panel" aria-labelledby={`${title.replaceAll(" ", "-").toLowerCase()}-title`}>
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id={`${title.replaceAll(" ", "-").toLowerCase()}-title`}>{title}</h2>
        </div>
        <div className="analytics-panel-heading-meta">
          <span>{periodLabel}</span>
          <ListPageNav
            label={paginationLabel}
            page={page}
            total={total}
            start={start}
            rangeEnd={rangeEnd}
            onPageChange={setPage}
          />
        </div>
      </div>
      <p className="analytics-note analytics-note-leading">{description}</p>
      {visible.length ? (
        <ul className="horizontal-bar-list concern-bar-list">
          {visible.map((item) => (
            <li key={item.label}>
              <div className="horizontal-bar-label">
                <span>{item.label}</span>
                <strong>
                  {formatNumber(item.count)} scans · {((item.count / maxCount) * 100).toFixed(0)}%
                </strong>
              </div>
              <div className="horizontal-bar-track" aria-hidden="true">
                <span style={{ width: `${(item.count / maxCount) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="empty-copy">{emptyMessage}</p>
      )}
    </section>
  );
}

function OutcomeMix({ data }: { data: ConsumerTrendsResponse }) {
  const total = data.summary.totalScans;
  const safePercent = total ? Math.round((data.summary.safeCount / total) * 100) : 0;
  const warningPercent = total ? Math.round((data.summary.warningCount / total) * 100) : 0;
  const safeEnd = safePercent;
  const warningEnd = Math.min(100, safePercent + warningPercent);

  return (
    <details className="analytics-panel outcome-panel">
      <summary className="analytics-panel-heading">
        <div>
          <p className="eyebrow">Scan outcomes</p>
          <h2 id="outcome-title">Scan Verdict Mix</h2>
        </div>
        <div className="analytics-panel-heading-meta">
          <span>{formatDate(data.period.from)} – {formatDate(data.period.to)}</span>
          <span className="outcome-panel-toggle">mix</span>
        </div>
      </summary>
      <div className="outcome-content">
        <div
          className="outcome-donut"
          role="img"
          aria-label={`${safePercent}% safe, ${warningPercent}% warning, ${Math.max(0, 100 - warningEnd)}% unsafe`}
          style={{
            background: `conic-gradient(var(--safe) 0 ${safeEnd}%, var(--warning) ${safeEnd}% ${warningEnd}%, var(--avoid) ${warningEnd}% 100%)`,
          }}
        >
          <div>
            <strong>{formatNumber(total)}</strong>
            <span>scans</span>
          </div>
        </div>
        <table className="outcome-legend">
          <caption>Exact scan verdict counts and percentages</caption>
          <thead>
            <tr>
              <th scope="col">Verdict</th>
              <th scope="col">Scans</th>
              <th scope="col">Share</th>
            </tr>
          </thead>
          <tbody>
            <OutcomeLegendRow label="SAFE" value={data.summary.safeCount} total={total} className="is-safe" />
            <OutcomeLegendRow label="WARNING" value={data.summary.warningCount} total={total} className="is-warning" />
            <OutcomeLegendRow label="UNSAFE" value={data.summary.unsafeCount} total={total} className="is-unsafe" />
          </tbody>
        </table>
      </div>
    </details>
  );
}

function OutcomeLegendRow({
  label,
  value,
  total,
  className,
}: {
  label: string;
  value: number;
  total: number;
  className: string;
}) {
  const share = total === 0 ? "0.0" : ((value / total) * 100).toFixed(1);
  return (
    <tr className={`outcome-tile ${className}`}>
      <th scope="row"><span className={`legend-dot ${className}`} aria-hidden="true" />{label}</th>
      <td>{formatNumber(value)}</td>
      <td>{share}%</td>
    </tr>
  );
}

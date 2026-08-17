import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { EmptyState, ErrorState, LoadingState } from "../../shared/ui/PageState";
import { consumerTrendsApiService } from "./consumerTrendsApiService";
import { buildPeriodQuery } from "./consumerTrendsDateRange";
import { downloadConsumerTrendsReport } from "./consumerTrendsReport";
import type {
  CategoryScanTrend,
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
  ProductScanTrend,
} from "./consumerTrendsTypes";

const PERIOD_OPTIONS = [7, 30, 90] as const;
const PRODUCTS_PER_PAGE = 5;

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
  const [periodDays, setPeriodDays] = useState<(typeof PERIOD_OPTIONS)[number]>(30);
  const [query, setQuery] = useState<ConsumerTrendsQuery>(() => buildPeriodQuery(30));
  const [data, setData] = useState<ConsumerTrendsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [productPage, setProductPage] = useState(0);
  const exportInProgress = useRef(false);
  const latestLoadRequest = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++latestLoadRequest.current;
    setLoading(true);
    setError(null);
    setExportError(null);
    setExportSuccess(false);

    try {
      const response = await consumerTrendsApiService.getConsumerTrends(query);
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
    const days = Number(value) as (typeof PERIOD_OPTIONS)[number];
    if (!PERIOD_OPTIONS.includes(days)) {
      return;
    }

    setPeriodDays(days);
    setProductPage(0);
    setQuery(buildPeriodQuery(days, query.category));
  };

  const updateCategory = (category: string) => {
    setProductPage(0);
    setQuery(buildPeriodQuery(periodDays, category || undefined));
  };

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
      <header className="page-header analytics-header">
        <div>
          <p className="eyebrow">ADMIN ANALYTICS</p>
          <h1>Consumer Trends</h1>
          <p>
            Aggregated scan activity and dietary-concern insights. Scan activity indicates consumer interest,
            not actual sales.
          </p>
        </div>

        <div className="analytics-controls" aria-label="Consumer trends filters">
          <label>
            Period
            <select value={periodDays} onChange={(event) => updatePeriod(event.target.value)} disabled={loading}>
              {PERIOD_OPTIONS.map((days) => (
                <option key={days} value={days}>
                  Last {days} Days
                </option>
              ))}
            </select>
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

          <button type="button" className="button button-secondary" onClick={() => void load()} disabled={loading}>
            {loading ? "Refreshing…" : "Refresh"}
          </button>

          <button
            type="button"
            className="button button--primary"
            onClick={() => void generateReport()}
            disabled={!canExport}
            aria-describedby="consumer-trends-export-help"
          >
            {exporting ? "Generating…" : "Generate CSV Report"}
          </button>
        </div>
      </header>

      <p id="consumer-trends-export-help" className="analytics-export-help">
        Exports the currently loaded anonymous aggregate data only. Raw scans and personal information are excluded.
      </p>

      {exportError ? <p className="form-message form-message--error" role="alert">{exportError}</p> : null}
      {exportSuccess ? (
        <p className="form-message form-message--success" role="status">Consumer trends report downloaded.</p>
      ) : null}

      {loading && !data ? <LoadingState label="Loading consumer trends…" /> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {!error && data ? (
        <ConsumerTrendsResult
          data={data}
          productPage={productPage}
          onProductPageChange={setProductPage}
          selectedCategory={selectedCategory}
          onCategoryChange={updateCategory}
        />
      ) : null}
    </div>
  );
}

function ConsumerTrendsResult({
  data,
  productPage,
  onProductPageChange,
  selectedCategory,
  onCategoryChange,
}: {
  data: ConsumerTrendsResponse;
  productPage: number;
  onProductPageChange: (page: number) => void;
  selectedCategory: string;
  onCategoryChange: (category: string) => void;
}) {
  useEffect(() => {
    onProductPageChange(0);
  }, [data.period.from, data.period.to, data.appliedFilters.category, onProductPageChange]);

  const noActivity = data.summary.totalScans === 0;

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

      <div className="analytics-two-column">
        <ProductRankingChart
          products={data.mostScannedProducts}
          page={productPage}
          onPageChange={onProductPageChange}
        />
        <CategoryOverviewChart
          categories={data.categoryOverview}
          selectedCategory={selectedCategory}
          onCategoryChange={onCategoryChange}
        />
      </div>

      <div className="analytics-two-column">
        <ConcernBars
          title="Most Frequently Triggered Dietary Restrictions"
          description="Counts show scan-triggered dietary-concern signals, not population prevalence."
          items={data.topRestrictions.map((item) => ({ label: item.restrictionCode, count: item.flaggedCount }))}
          emptyMessage="No dietary restrictions were triggered in the selected period."
        />
        <ConcernBars
          title="Top Flagged Ingredients"
          description="Counts show ingredients flagged in scan findings, not population prevalence."
          items={data.topFlaggedIngredients.map((item) => ({ label: item.ingredientName, count: item.flaggedCount }))}
          emptyMessage="No ingredients were flagged in the selected period."
        />
      </div>

      <OutcomeMix data={data} />
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
  const width = 760;
  const height = 260;
  const padding = 42;
  const plotWidth = width - padding * 2;
  const plotHeight = height - padding * 2;
  const maxValue = Math.max(1, ...daily.map((item) => item.totalCount));
  const pointFor = (index: number, value: number) => {
    const x = daily.length <= 1 ? width / 2 : padding + (index / (daily.length - 1)) * plotWidth;
    const y = height - padding - (value / maxValue) * plotHeight;
    return { x, y };
  };
  const points = daily.map((item, index) => {
    const point = pointFor(index, item.totalCount);
    return `${point.x},${point.y}`;
  });
  const labelInterval = daily.length <= 7 ? 1 : daily.length <= 30 ? 5 : 15;

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
          role="img"
          aria-label="Line chart of total scans for every day in the selected period"
        >
          <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} className="chart-axis" />
          <line x1={padding} y1={padding} x2={padding} y2={height - padding} className="chart-axis" />
          <line x1={padding} y1={padding} x2={width - padding} y2={padding} className="chart-grid-line" />
          <text x={padding - 10} y={padding + 4} textAnchor="end" className="chart-axis-label">
            {maxValue}
          </text>
          <text x={padding - 10} y={height - padding + 4} textAnchor="end" className="chart-axis-label">
            0
          </text>
          <text
            x={14}
            y={height / 2}
            textAnchor="middle"
            className="chart-axis-label"
            transform={`rotate(-90 14 ${height / 2})`}
          >
            Scans
          </text>
          {points.length > 1 ? <polyline points={points.join(" ")} className="chart-line" /> : null}
          {daily.map((item, index) => {
            const point = pointFor(index, item.totalCount);
            const showLabel = index % labelInterval === 0 || index === daily.length - 1;
            return (
              <g key={item.date}>
                <circle
                  cx={point.x}
                  cy={point.y}
                  r="5"
                  className="chart-point"
                  tabIndex={0}
                  aria-label={`${formatDate(item.date)}: ${item.totalCount} total scans, ${item.safeCount} safe, ${item.warningCount} warning, ${item.unsafeCount} unsafe`}
                >
                  <title>{`${formatDate(item.date)} — ${item.totalCount} scans`}</title>
                </circle>
                {showLabel ? (
                  <text x={point.x} y={height - 14} textAnchor="middle" className="chart-axis-label">
                    {formatShortDate(item.date)}
                  </text>
                ) : null}
              </g>
            );
          })}
        </svg>
      </div>

      <details className="analytics-data-table">
        <summary>View daily values</summary>
        <div className="table-scroll">
          <table>
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
              {daily.map((item) => (
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

function ProductRankingChart({
  products,
  page,
  onPageChange,
}: {
  products: ProductScanTrend[];
  page: number;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(products.length / PRODUCTS_PER_PAGE));
  const safePage = Math.min(page, totalPages - 1);
  const start = safePage * PRODUCTS_PER_PAGE;
  const visibleProducts = products.slice(start, start + PRODUCTS_PER_PAGE);
  const maxCount = Math.max(1, ...products.map((item) => item.scanCount));
  const rangeEnd = Math.min(start + PRODUCTS_PER_PAGE, products.length);

  return (
    <section className="analytics-panel" aria-labelledby="products-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">PRODUCT INTEREST</p>
          <h2 id="products-title">Most Scanned Products</h2>
        </div>
        {products.length ? <span>{start + 1}–{rangeEnd} of {products.length}</span> : null}
      </div>

      {visibleProducts.length ? (
        <ol className="horizontal-bar-list product-bar-list" start={start + 1}>
          {visibleProducts.map((product) => (
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

      {products.length > PRODUCTS_PER_PAGE ? (
        <nav className="analytics-pagination" aria-label="Product ranking pages">
          <button type="button" className="button button-secondary" disabled={safePage === 0} onClick={() => onPageChange(safePage - 1)}>
            Previous
          </button>
          <span>Page {safePage + 1} of {totalPages}</span>
          <button
            type="button"
            className="button button-secondary"
            disabled={safePage >= totalPages - 1}
            onClick={() => onPageChange(safePage + 1)}
          >
            Next
          </button>
        </nav>
      ) : null}

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
}: {
  categories: CategoryScanTrend[];
  selectedCategory: string;
  onCategoryChange: (category: string) => void;
}) {
  const maxCount = Math.max(1, ...categories.map((item) => item.scanCount));

  return (
    <section className="analytics-panel" aria-labelledby="categories-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">FULL-PERIOD MIX</p>
          <h2 id="categories-title">Scan Activity by Category</h2>
        </div>
        {selectedCategory ? (
          <button type="button" className="text-button" onClick={() => onCategoryChange("")}>
            Show all
          </button>
        ) : null}
      </div>

      {categories.length ? (
        <div className="category-bar-list">
          {categories.map((item) => {
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
      <p className="analytics-note">Select a category to apply it to every chart and summary above.</p>
    </section>
  );
}

function ConcernBars({
  title,
  description,
  items,
  emptyMessage,
}: {
  title: string;
  description: string;
  items: Array<{ label: string; count: number }>;
  emptyMessage: string;
}) {
  const maxCount = Math.max(1, ...items.map((item) => item.count));

  return (
    <section className="analytics-panel" aria-labelledby={`${title.replaceAll(" ", "-").toLowerCase()}-title`}>
      <div className="analytics-panel-heading">
        <h2 id={`${title.replaceAll(" ", "-").toLowerCase()}-title`}>{title}</h2>
      </div>
      <p className="analytics-note analytics-note-leading">{description}</p>
      {items.length ? (
        <ul className="horizontal-bar-list concern-bar-list">
          {items.map((item) => (
            <li key={item.label}>
              <div className="horizontal-bar-label">
                <span>{item.label}</span>
                <strong>{formatNumber(item.count)} scans</strong>
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
    <section className="analytics-panel outcome-panel" aria-labelledby="outcome-title">
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">SUPPORTING VIEW</p>
          <h2 id="outcome-title">Scan Verdict Mix</h2>
        </div>
        <span>{formatDate(data.period.from)} – {formatDate(data.period.to)}</span>
      </div>
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
    </section>
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
  return (
    <tr>
      <th scope="row"><span className={`legend-dot ${className}`} aria-hidden="true" />{label}</th>
      <td>{formatNumber(value)}</td>
      <td>{total === 0 ? "0.0" : ((value / total) * 100).toFixed(1)}%</td>
    </tr>
  );
}

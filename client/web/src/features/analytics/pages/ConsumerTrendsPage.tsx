import { useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { consumerTrendsApiService } from '../api/consumerTrendsApiService'
import {
  CategoryOverviewChart,
  ConcernBars,
  DailyActivityChart,
  OutcomeMix,
  ProductRankingChart,
  SummaryCard,
} from '../components/ConsumerTrendsCharts'
import {
  PERIOD_OPTIONS,
  buildPeriodQuery,
  describeRangeError,
  matchingPresetDays,
  singaporeToday,
} from '../lib/consumerTrendsDateRange'
import { formatDate, formatNumber } from '../lib/consumerTrendsFormat'
import { downloadConsumerTrendsReport } from '../lib/consumerTrendsReport'
import type {
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
} from '../api/consumerTrendsTypes'

const INGREDIENT_RANKING_LIMIT = 20

const SUMMARY_HELP = {
  totalScans: 'All product scans recorded in the selected period, including repeats of the same product.',
  uniqueProducts: 'Distinct products that were scanned at least once in the selected period.',
  averageScansPerDay: 'Total scans divided by the number of days in the selected period.',
  peakScanDay: 'The calendar day with the most scans in the selected period.',
}

export function ConsumerTrendsPage() {
  const today = singaporeToday()
  const [query, setQuery] = useState<ConsumerTrendsQuery>(() => buildPeriodQuery(30))
  const [fromInput, setFromInput] = useState(() => query.from ?? '')
  const [toInput, setToInput] = useState(() => query.to ?? '')
  const [rangeError, setRangeError] = useState<string | null>(null)
  const [data, setData] = useState<ConsumerTrendsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const [exportSuccess, setExportSuccess] = useState(false)
  const [exporting, setExporting] = useState(false)
  const exportInProgress = useRef(false)
  const latestLoadRequest = useRef(0)
  const [reloadNonce, setReloadNonce] = useState(0)

  useEffect(() => {
    const requestId = ++latestLoadRequest.current
    const request = window.setTimeout(() => {
      void (async () => {
        setLoading(true)
        setError(null)
        setExportError(null)
        setExportSuccess(false)

        try {
          const response = await consumerTrendsApiService.getConsumerTrends({
            ...query,
            limit: INGREDIENT_RANKING_LIMIT,
          })
          if (requestId === latestLoadRequest.current) {
            setData(response)
          }
        } catch (error_) {
          if (requestId === latestLoadRequest.current) {
            setError(getErrorMessage(error_))
          }
        } finally {
          if (requestId === latestLoadRequest.current) {
            setLoading(false)
          }
        }
      })()
    }, 0)
    return () => window.clearTimeout(request)
  }, [query, reloadNonce])

  const load = () => setReloadNonce((nonce) => nonce + 1)

  const updatePeriod = (value: string) => {
    if (value === 'custom') return
    const days = Number(value) as (typeof PERIOD_OPTIONS)[number]
    if (!PERIOD_OPTIONS.includes(days)) return
    const nextQuery = buildPeriodQuery(days, query.category)
    setRangeError(null)
    setFromInput(nextQuery.from ?? '')
    setToInput(nextQuery.to ?? '')
    setQuery(nextQuery)
  }

  const applyCustomRange = (from: string, to: string) => {
    const message = describeRangeError(from, to, today)
    if (message) {
      setRangeError(message)
      return
    }
    setRangeError(null)
    setQuery({
      from,
      to,
      category: query.category,
    })
  }

  const updateCategory = (category: string) => {
    setQuery({
      ...query,
      category: category || undefined,
    })
  }

  const presetDays = matchingPresetDays(query.from, query.to, today)

  const categoryOptions = data?.categoryOverview?.map((item) => item.category) ?? []
  const selectedCategory = query.category ?? ''
  const dataMatchesCurrentQuery = data !== null
    && data.period.from === query.from
    && data.period.to === query.to
    && (data.appliedFilters.category ?? '') === selectedCategory
  const canExport = data !== null
    && dataMatchesCurrentQuery
    && data.summary.totalScans > 0
    && !loading
    && !error
    && !exporting

  const generateReport = async () => {
    if (!data || !canExport || exportInProgress.current) return

    exportInProgress.current = true
    setExporting(true)
    setExportError(null)
    setExportSuccess(false)
    try {
      await downloadConsumerTrendsReport(data)
      setExportSuccess(true)
    } catch {
      setExportError('The report could not be downloaded. No file was saved. Please try again.')
    } finally {
      exportInProgress.current = false
      setExporting(false)
    }
  }

  return (
    <div className="admin-page analytics-page">
      <header className="page-header page-header--split analytics-header">
        <div>
          <p className="eyebrow">System Administration</p>
          <h1>Consumer Trends</h1>
          <p>
            Aggregated scan activity and dietary-concern insights. Scan activity indicates consumer interest,
            not actual sales.
          </p>
        </div>

        <div className="analytics-toolbar">
          <div className="analytics-controls" aria-label="Consumer trends filters">
            <label>
              <span>Period</span>
              <select
                value={presetDays ?? 'custom'}
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
              <span>From</span>
              <input
                type="date"
                value={fromInput}
                max={today}
                disabled={loading}
                onChange={(event) => {
                  const nextFrom = event.target.value
                  setFromInput(nextFrom)
                  applyCustomRange(nextFrom, toInput)
                }}
              />
            </label>

            <label>
              <span>To</span>
              <input
                type="date"
                value={toInput}
                max={today}
                disabled={loading}
                onChange={(event) => {
                  const nextTo = event.target.value
                  setToInput(nextTo)
                  applyCustomRange(fromInput, nextTo)
                }}
              />
            </label>

            <label>
              <span>Product Category</span>
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
              onClick={load}
              disabled={loading}
            >
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
            <button
              type="button"
              className="button button--primary"
              onClick={() => void generateReport()}
              disabled={!canExport}
              aria-describedby="consumer-trends-export-help"
            >
              {exporting ? 'Generating…' : 'Generate Report'}
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
        <output className="form-message form-message--success">Consumer trends report downloaded.</output>
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
  )
}

function ConsumerTrendsResult({
  data,
  selectedCategory,
  onCategoryChange,
}: Readonly<{
  data: ConsumerTrendsResponse
  selectedCategory: string
  onCategoryChange: (category: string) => void
}>) {
  const noActivity = data.summary.totalScans === 0
  const listResetKey = `${data.period.from}|${data.period.to}|${data.appliedFilters.category ?? ''}`
  const periodLabel = `${formatDate(data.period.from)} – ${formatDate(data.period.to)}`

  return (
    <>
      <section className="analytics-summary-grid" aria-label="Consumer trends summary">
        <SummaryCard
          label="Total Scans"
          value={formatNumber(data.summary.totalScans)}
          title={SUMMARY_HELP.totalScans}
        />
        <SummaryCard
          label="Unique Products Scanned"
          value={formatNumber(data.summary.uniqueProducts)}
          title={SUMMARY_HELP.uniqueProducts}
        />
        <SummaryCard
          label="Average Scans per Day"
          value={data.summary.averageScansPerDay.toFixed(2)}
          title={SUMMARY_HELP.averageScansPerDay}
        />
        <SummaryCard
          label="Peak Scan Day"
          value={data.summary.peakScanDay ? formatDate(data.summary.peakScanDay.date) : 'No activity'}
          detail={data.summary.peakScanDay ? `${formatNumber(data.summary.peakScanDay.scanCount)} scans` : undefined}
          title={SUMMARY_HELP.peakScanDay}
        />
      </section>

      {data.dataQuality.partial ? (
        <output className="analytics-warning">
          <strong>Partial dietary-concern data:</strong> {formatNumber(data.dataQuality.skippedMalformedFindings)} scan finding records could not be read.
        </output>
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
  )
}

import { useState, type ReactNode } from 'react'
import { HoverTip } from '../../../shared/ui/HoverTip'
import {
  chartEndLabelIndexes,
  consumerTrendsChartAxis,
} from '../lib/consumerTrendsChartAxis'
import { formatDate, formatNumber, formatShortDate } from '../lib/consumerTrendsFormat'
import { CONSUMER_TRENDS_ROWS_PER_PAGE, usePagedItems } from '../lib/consumerTrendsPaging'
import type {
  CategoryScanTrend,
  ConsumerTrendsResponse,
  ProductScanTrend,
} from '../api/consumerTrendsTypes'

function axisTextAnchor(index: number, lastIndex: number): 'start' | 'middle' | 'end' {
  if (index === 0) return 'start'
  if (index === lastIndex) return 'end'
  return 'middle'
}

function ListPageNav({
  label,
  page,
  total,
  start,
  rangeEnd,
  onPageChange,
}: Readonly<{
  label: string
  page: number
  total: number
  start: number
  rangeEnd: number
  onPageChange: (page: number) => void
}>) {
  if (total === 0) return null
  const totalPages = Math.max(1, Math.ceil(total / CONSUMER_TRENDS_ROWS_PER_PAGE))
  const rangeText = `${start + 1}–${rangeEnd} of ${total}`
  if (total <= CONSUMER_TRENDS_ROWS_PER_PAGE) return <span>{rangeText}</span>
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
  )
}

export function SummaryCard({
  label,
  value,
  detail,
  title,
}: Readonly<{
  label: string
  value: ReactNode
  detail?: string
  title: string
}>) {
  return (
    <HoverTip text={title} className="hover-tip--block">
      <article className="analytics-card summary-card">
        <p className="analytics-label">{label}</p>
        <strong>{value}</strong>
        {detail ? <span>{detail}</span> : null}
      </article>
    </HoverTip>
  )
}

export function DailyActivityChart({
  daily,
}: Readonly<{ daily: ConsumerTrendsResponse['dailyTrend'] }>) {
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
    ? `${pointFor(0, 0).x},${baseline} ${linePoints.join(' ')} ${pointFor(daily.length - 1, 0).x},${baseline}`
    : ''
  const xLabelIndexes = chartEndLabelIndexes(daily.length)
  const hovered = hoveredIndex === null ? undefined : daily[hoveredIndex]
  const hoveredPoint = hovered && hoveredIndex !== null
    ? pointFor(hoveredIndex, hovered.totalCount)
    : null
  const dailyResetKey = `${daily[0]?.date ?? ''}|${daily.at(-1)?.date ?? ''}|${daily.length}`
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
          {linePoints.length > 1 ? <polyline points={linePoints.join(' ')} className="chart-line" /> : null}
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
            const textAnchor = axisTextAnchor(index, daily.length - 1)
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
  )
}

export function ProductRankingChart({
  products,
  resetKey,
  periodLabel,
}: Readonly<{
  products: ProductScanTrend[]
  resetKey: string
  periodLabel: string
}>) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(products, resetKey)
  const maxCount = Math.max(1, ...products.map((item) => item.scanCount))

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
  )
}

export function CategoryOverviewChart({
  categories,
  selectedCategory,
  onCategoryChange,
  resetKey,
  periodLabel,
}: Readonly<{
  categories: CategoryScanTrend[]
  selectedCategory: string
  onCategoryChange: (category: string) => void
  resetKey: string
  periodLabel: string
}>) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(categories, resetKey)
  const maxCount = Math.max(1, ...categories.map((item) => item.scanCount))

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
            <button type="button" className="text-button" onClick={() => onCategoryChange('')}>
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
            const selected = selectedCategory === item.category
            return (
              <button
                key={item.category}
                type="button"
                className={`category-bar-row${selected ? ' is-selected' : ''}`}
                aria-pressed={selected}
                onClick={() => onCategoryChange(selected ? '' : item.category)}
              >
                <span className="horizontal-bar-label">
                  <span>{item.category}</span>
                  <strong>{formatNumber(item.scanCount)} scans · {item.percentage.toFixed(1)}%</strong>
                </span>
                <span className="horizontal-bar-track" aria-hidden="true">
                  <span style={{ width: `${(item.scanCount / maxCount) * 100}%` }} />
                </span>
              </button>
            )
          })}
        </div>
      ) : (
        <p className="empty-copy">No category activity is available for this period.</p>
      )}
      <p className="analytics-note">
        This mix uses the selected dates. Choosing a category still leaves this mix unchanged and applies the filter to the other charts.
      </p>
    </section>
  )
}

export function ConcernBars({
  eyebrow,
  title,
  description,
  items,
  emptyMessage,
  paginationLabel,
  resetKey,
  periodLabel,
}: Readonly<{
  eyebrow: string
  title: string
  description: string
  items: Array<{ label: string; count: number }>
  emptyMessage: string
  paginationLabel: string
  resetKey: string
  periodLabel: string
}>) {
  const { page, setPage, start, visible, rangeEnd, total } = usePagedItems(items, resetKey)
  const maxCount = Math.max(1, ...items.map((item) => item.count))

  return (
    <section className="analytics-panel" aria-labelledby={`${title.replaceAll(' ', '-').toLowerCase()}-title`}>
      <div className="analytics-panel-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id={`${title.replaceAll(' ', '-').toLowerCase()}-title`}>{title}</h2>
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
  )
}

export function OutcomeMix({ data }: Readonly<{ data: ConsumerTrendsResponse }>) {
  const total = data.summary.totalScans
  const safePercent = total ? Math.round((data.summary.safeCount / total) * 100) : 0
  const warningPercent = total ? Math.round((data.summary.warningCount / total) * 100) : 0
  const safeEnd = safePercent
  const warningEnd = Math.min(100, safePercent + warningPercent)

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
  )
}

function OutcomeLegendRow({
  label,
  value,
  total,
  className,
}: Readonly<{
  label: string
  value: number
  total: number
  className: string
}>) {
  const share = total === 0 ? '0.0' : ((value / total) * 100).toFixed(1)
  return (
    <tr className={`outcome-tile ${className}`}>
      <th scope="row"><span className={`legend-dot ${className}`} aria-hidden="true" />{label}</th>
      <td>{formatNumber(value)}</td>
      <td>{share}%</td>
    </tr>
  )
}

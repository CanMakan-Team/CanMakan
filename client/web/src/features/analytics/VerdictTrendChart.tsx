import type { DailyTrendPoint } from './consumerTrendsTypes'

/**
 * UC14 - View Scan Verdict Trend.
 *
 * Renders the daily Safe / Warning / Unsafe scan counts as a stacked area chart, matching the
 * hand-rolled SVG/CSS charts used elsewhere on the admin pages (no chart library). The verdict
 * colours are the same ones the verdict-distribution donut uses. A visually-hidden table mirrors the
 * data for screen-reader and no-CSS access, following the page convention.
 */

const SAFE_COLOR = '#27875b'
const WARNING_COLOR = '#d6a12b'
const UNSAFE_COLOR = '#b24b44'

const WIDTH = 720
const HEIGHT = 240
const PADDING = { top: 16, right: 16, bottom: 28, left: 40 }

function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, (month ?? 1) - 1, day ?? 1)
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export function VerdictTrendChart({ points }: { points: DailyTrendPoint[] }) {
  if (points.length === 0) {
    return <p>No daily trend data was available for this period.</p>
  }

  const innerWidth = WIDTH - PADDING.left - PADDING.right
  const innerHeight = HEIGHT - PADDING.top - PADDING.bottom
  const maxTotal = Math.max(1, ...points.map((point) => point.totalCount))
  const count = points.length
  const baseline = PADDING.top + innerHeight

  const xAt = (index: number): number =>
    count === 1
      ? PADDING.left + innerWidth / 2
      : PADDING.left + (index / (count - 1)) * innerWidth
  const yAt = (value: number): number =>
    PADDING.top + innerHeight - (value / maxTotal) * innerHeight

  const safeTop = points.map((point) => yAt(point.safeCount))
  const warningTop = points.map((point) => yAt(point.safeCount + point.warningCount))
  const totalTop = points.map((point) => yAt(point.totalCount))

  const areaToBaseline = (topYs: number[]): string => {
    const top = topYs.map((yy, index) => `${xAt(index)},${yy}`)
    return [...top, `${xAt(count - 1)},${baseline}`, `${xAt(0)},${baseline}`].join(' ')
  }
  const areaBetween = (topYs: number[], bottomYs: number[]): string => {
    const top = topYs.map((yy, index) => `${xAt(index)},${yy}`)
    const bottom = bottomYs.map((yy, index) => `${xAt(index)},${yy}`).reverse()
    return [...top, ...bottom].join(' ')
  }

  const yTicks = [0, Math.round(maxTotal / 2), maxTotal].filter(
    (tick, index, all) => all.indexOf(tick) === index,
  )
  const xLabelIndexes = [0, Math.floor((count - 1) / 2), count - 1].filter(
    (value, index, all) => all.indexOf(value) === index,
  )

  return (
    <>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        style={{ width: '100%', height: 'auto' }}
        role="img"
        aria-label="Stacked area chart of safe, warning and unsafe scan counts per day."
      >
        {yTicks.map((tick) => (
          <g key={tick}>
            <line
              x1={PADDING.left}
              y1={yAt(tick)}
              x2={WIDTH - PADDING.right}
              y2={yAt(tick)}
              stroke="#eceae3"
            />
            <text x={PADDING.left - 6} y={yAt(tick) + 3} textAnchor="end" fontSize="10" fill="#8a938d">
              {tick}
            </text>
          </g>
        ))}

        <polygon fill={SAFE_COLOR} fillOpacity="0.9" points={areaToBaseline(safeTop)} />
        <polygon fill={WARNING_COLOR} fillOpacity="0.9" points={areaBetween(warningTop, safeTop)} />
        <polygon fill={UNSAFE_COLOR} fillOpacity="0.92" points={areaBetween(totalTop, warningTop)} />

        {xLabelIndexes.map((index) => (
          <text
            key={index}
            x={xAt(index)}
            y={HEIGHT - 8}
            fontSize="10"
            fill="#8a938d"
            textAnchor={index === 0 ? 'start' : index === count - 1 ? 'end' : 'middle'}
          >
            {formatDay(points[index].date)}
          </text>
        ))}
      </svg>

      <table className="compact-table accessible-equivalent">
        <caption>Accessible daily verdict trend values</caption>
        <thead>
          <tr>
            <th>Date</th>
            <th>Safe</th>
            <th>Warning</th>
            <th>Unsafe</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.date}>
              <td>{formatDay(point.date)}</td>
              <td>{point.safeCount.toLocaleString()}</td>
              <td>{point.warningCount.toLocaleString()}</td>
              <td>{point.unsafeCount.toLocaleString()}</td>
              <td>{point.totalCount.toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

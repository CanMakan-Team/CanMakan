export interface VerdictTrendPoint {
  date: string
  safeCount: number
  warningCount: number
  unsafeCount: number
  totalCount: number
}

/**
 * UC14 - View Scan Verdict Trend.
 *
 * Renders the daily Safe / Warning / Unsafe scan counts as a multi-line chart, matching the
 * hand-rolled SVG/CSS charts used elsewhere on the admin pages (no chart library). The y-axis uses a
 * minimum ceiling (at least 10) and rounds up to a "nice" value for larger datasets, so a family that
 * only scans a few items per day still gets a calm, readable line near the baseline rather than a
 * spiky graph that fills the whole panel. Colours are the same ones the verdict-distribution donut
 * uses. A visually-hidden table mirrors the data for screen-reader and no-CSS access.
 */

const SAFE_COLOR = '#27875b'
const WARNING_COLOR = '#d6a12b'
const UNSAFE_COLOR = '#b24b44'

const WIDTH = 720
const HEIGHT = 240
const PADDING = { top: 16, right: 16, bottom: 28, left: 40 }

// The y-axis never shrinks below this, so sparse data (about one scan per day) draws a gentle line
// near the baseline instead of stretching to fill the panel.
const MINIMUM_AXIS_MAX = 10

function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, (month ?? 1) - 1, day ?? 1)
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

/**
 * Chooses a rounded step size (1, 2, 5, 10, 20, ...) that splits the axis into roughly five
 * divisions, so the gridline labels stay whole numbers and evenly spaced.
 */
function niceStep(target: number): number {
  const rough = target / 5
  const power = Math.pow(10, Math.floor(Math.log10(rough)))
  const normalised = rough / power
  const stepFactor = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10
  return stepFactor * power
}

export function VerdictTrendChart({ points }: { points: VerdictTrendPoint[] }) {
  if (points.length === 0) {
    return <p>No daily trend data was available for this period.</p>
  }

  const innerWidth = WIDTH - PADDING.left - PADDING.right
  const innerHeight = HEIGHT - PADDING.top - PADDING.bottom
  const dataMax = Math.max(0, ...points.map((point) => point.totalCount))
  const target = Math.max(dataMax, MINIMUM_AXIS_MAX)
  const step = niceStep(target)
  const axisMax = Math.ceil(target / step) * step
  const count = points.length
  const baseline = PADDING.top + innerHeight

  const centerAt = (index: number): number =>
    count === 1 ? PADDING.left + innerWidth / 2 : PADDING.left + (innerWidth * index) / (count - 1)
  const yOf = (value: number): number => baseline - (value / axisMax) * innerHeight

  const yTicks: number[] = []
  for (let tick = 0; tick <= axisMax; tick += step) {
    yTicks.push(tick)
  }

  const xLabelIndexes = [0, Math.floor((count - 1) / 2), count - 1].filter(
    (value, index, all) => all.indexOf(value) === index,
  )

  const series = [
    { key: 'safe', color: SAFE_COLOR, valueOf: (point: VerdictTrendPoint) => point.safeCount },
    { key: 'warning', color: WARNING_COLOR, valueOf: (point: VerdictTrendPoint) => point.warningCount },
    { key: 'unsafe', color: UNSAFE_COLOR, valueOf: (point: VerdictTrendPoint) => point.unsafeCount },
  ]

  return (
    <>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        style={{ width: '100%', height: 'auto' }}
        role="img"
        aria-label="Line chart of safe, warning and unsafe scan counts per day."
      >
        {yTicks.map((tick) => (
          <g key={tick}>
            <line x1={PADDING.left} y1={yOf(tick)} x2={WIDTH - PADDING.right} y2={yOf(tick)} stroke="#eceae3" />
            <text x={PADDING.left - 6} y={yOf(tick) + 3} textAnchor="end" fontSize="10" fill="#8a938d">
              {tick}
            </text>
          </g>
        ))}

        {series.map((line) => {
          const linePoints = points.map((point, index) => `${centerAt(index)},${yOf(line.valueOf(point))}`).join(' ')
          // Close the line back down to the baseline so the enclosed area can be filled with colour.
          const areaPoints = `${centerAt(0)},${baseline} ${linePoints} ${centerAt(count - 1)},${baseline}`
          return (
            <g key={line.key}>
              <polygon points={areaPoints} fill={line.color} fillOpacity={0.18} stroke="none" />
              <polyline points={linePoints} fill="none" stroke={line.color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
            </g>
          )
        })}

        {xLabelIndexes.map((index) => (
          <text
            key={index}
            x={centerAt(index)}
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

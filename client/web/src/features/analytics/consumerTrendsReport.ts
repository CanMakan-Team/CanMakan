import type { ConsumerTrendsResponse } from './consumerTrendsTypes'

export const CONSUMER_TRENDS_REPORT_MIME_TYPE = 'text/csv;charset=utf-8'

type CsvValue = string | number | boolean | null | undefined
type CsvRow = CsvValue[]

const FORMULA_PREFIX = /^\s*[=+\-@]/u
const INVALID_FILENAME_CHARACTERS = /[^\p{L}\p{N}]+/gu

function safeCsvText(value: string): string {
  return FORMULA_PREFIX.test(value) ? `'${value}` : value
}

function encodeCsvCell(value: CsvValue): string {
  const text = typeof value === 'string'
    ? safeCsvText(value)
    : value === null || value === undefined
      ? ''
      : String(value)
  return /[",\r\n]/u.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function formatPercentage(value: number | null | undefined): string {
  return `${Number.isFinite(value) ? Number(value).toFixed(2) : '0.00'}%`
}

function formatTimestamp(value: string | Date | null | undefined): string {
  const date = value instanceof Date ? value : value ? new Date(value) : null
  if (date === null || Number.isNaN(date.getTime())) return 'Not available'

  return new Intl.DateTimeFormat('en-SG', {
    dateStyle: 'medium',
    timeStyle: 'long',
    timeZone: 'Asia/Singapore',
  }).format(date)
}

function appendSection(rows: CsvRow[], title: string, header: CsvRow, data: CsvRow[]) {
  rows.push([], [title], header)
  rows.push(...(data.length > 0 ? data : [['No data available']]))
}

export function buildConsumerTrendsCsv(
  data: ConsumerTrendsResponse,
  exportedAt: Date = new Date(),
): string {
  const summary = data.summary
  const category = data.appliedFilters?.category || 'All Categories'
  const rows: CsvRow[] = [
    ['CanMakan Consumer Trends Report'],
    [],
    ['Report Metadata'],
    ['Data generated at', formatTimestamp(data.generatedAt)],
    ['Exported at', formatTimestamp(exportedAt)],
    ['Selected date range', `${data.period.from} to ${data.period.to} (inclusive)`],
    ['Business timezone', data.period.timezone || 'Asia/Singapore'],
    ['Selected category', category],
    [
      'Data quality',
      data.dataQuality?.partial
        ? `Partial (${data.dataQuality.skippedMalformedFindings ?? 0} malformed finding records skipped)`
        : 'Complete',
    ],
  ]

  appendSection(rows, 'Summary Metrics', ['Metric', 'Value'], [
    ['Total scans', summary.totalScans],
    ['Safe scans', summary.safeCount],
    ['Warning scans', summary.warningCount],
    ['Unsafe scans', summary.unsafeCount],
    ['Unique products scanned', summary.uniqueProducts],
    ['Average scans per day', Number(summary.averageScansPerDay ?? 0).toFixed(2)],
    ['Peak scan day', summary.peakScanDay?.date ?? 'No activity'],
    ['Peak scan count', summary.peakScanDay?.scanCount ?? 0],
  ])

  appendSection(
    rows,
    'Daily Scan Trend',
    ['Date', 'Total', 'Safe', 'Warning', 'Unsafe'],
    (data.dailyTrend ?? []).map((point) => [
      point.date,
      point.totalCount,
      point.safeCount,
      point.warningCount,
      point.unsafeCount,
    ]),
  )

  appendSection(
    rows,
    'Top 5 Most Scanned Products',
    ['Rank', 'Product', 'Scan count', 'Share of filtered scans'],
    (data.mostScannedProducts ?? []).slice(0, 5).map((product) => [
      product.rank,
      product.productName,
      product.scanCount,
      formatPercentage(product.percentage),
    ]),
  )

  appendSection(
    rows,
    'Category Overview (full selected period)',
    ['Category', 'Scan count', 'Share of period scans'],
    (data.categoryOverview ?? []).map((item) => [
      item.category,
      item.scanCount,
      formatPercentage(item.percentage),
    ]),
  )

  appendSection(
    rows,
    'Restriction Trends',
    ['Restriction code', 'Flagged scan count'],
    (data.topRestrictions ?? []).map((item) => [item.restrictionCode, item.flaggedCount]),
  )

  appendSection(
    rows,
    'Flagged Ingredient Trends',
    ['Ingredient', 'Flagged scan count'],
    (data.topFlaggedIngredients ?? []).map((item) => [item.ingredientName, item.flaggedCount]),
  )

  const total = summary.totalScans
  appendSection(rows, 'Scan Verdict Mix', ['Verdict', 'Scan count', 'Share'], [
    ['SAFE', summary.safeCount, formatPercentage(total ? summary.safeCount / total * 100 : 0)],
    ['WARNING', summary.warningCount, formatPercentage(total ? summary.warningCount / total * 100 : 0)],
    ['UNSAFE', summary.unsafeCount, formatPercentage(total ? summary.unsafeCount / total * 100 : 0)],
  ])

  return `\uFEFF${rows.map((row) => row.map(encodeCsvCell).join(',')).join('\r\n')}\r\n`
}

function filenameSegment(value: string): string {
  return value
    .normalize('NFKC')
    .replace(INVALID_FILENAME_CHARACTERS, '-')
    .replace(/^-+|-+$/gu, '')
    .toLocaleLowerCase('en-SG')
    .slice(0, 48) || 'filtered'
}

export function buildConsumerTrendsFilename(data: ConsumerTrendsResponse): string {
  const category = data.appliedFilters?.category
    ? filenameSegment(data.appliedFilters.category)
    : 'all-categories'
  return `canmakan-consumer-trends_${data.period.from}_to_${data.period.to}_${category}.csv`
}

export async function downloadConsumerTrendsReport(data: ConsumerTrendsResponse): Promise<void> {
  const blob = new Blob([buildConsumerTrendsCsv(data)], {
    type: CONSUMER_TRENDS_REPORT_MIME_TYPE,
  })
  const objectUrl = URL.createObjectURL(blob)
  let link: HTMLAnchorElement | null = null

  try {
    link = document.createElement('a')
    link.href = objectUrl
    link.download = buildConsumerTrendsFilename(data)
    link.hidden = true
    document.body.append(link)
    link.click()
  } finally {
    link?.remove()
    URL.revokeObjectURL(objectUrl)
  }
}

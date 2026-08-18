import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { VerdictTrendChart, type VerdictTrendPoint } from '../../../features/analytics/components/VerdictTrendChart'

const twoDays: VerdictTrendPoint[] = [
  { date: '2026-08-15', safeCount: 2, warningCount: 1, unsafeCount: 0, totalCount: 3 },
  { date: '2026-08-16', safeCount: 4, warningCount: 0, unsafeCount: 1, totalCount: 5 },
]

describe('VerdictTrendChart', () => {
  it('explains when there are no daily points', () => {
    render(<VerdictTrendChart points={[]} />)

    expect(
      screen.getByText('No daily trend data was available for this period.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('renders the chart, a single-day centre point, and the accessible table', () => {
    const oneDay: VerdictTrendPoint[] = [
      { date: '2026-08-16', safeCount: 12, warningCount: 3, unsafeCount: 1, totalCount: 16 },
    ]
    const { rerender } = render(<VerdictTrendChart points={oneDay} />)

    expect(
      screen.getByRole('img', {
        name: 'Line chart of safe, warning and unsafe scan counts per day.',
      }),
    ).toBeInTheDocument()

    rerender(<VerdictTrendChart points={twoDays} />)

    const table = screen.getByRole('table', { name: 'Accessible daily verdict trend values' })
    expect(within(table).getByRole('columnheader', { name: 'Safe' })).toBeInTheDocument()
    expect(within(table).getByText('4')).toBeInTheDocument()
    expect(within(table).getByText('5')).toBeInTheDocument()
  })

  it('uses a rounded axis when daily totals are large', () => {
    const busyDays: VerdictTrendPoint[] = [
      { date: '2026-08-14', safeCount: 40, warningCount: 10, unsafeCount: 5, totalCount: 55 },
      { date: '2026-08-15', safeCount: 80, warningCount: 20, unsafeCount: 10, totalCount: 110 },
      { date: '2026-08-16', safeCount: 20, warningCount: 5, unsafeCount: 2, totalCount: 27 },
    ]
    render(<VerdictTrendChart points={busyDays} />)

    expect(
      screen.getByRole('img', {
        name: 'Line chart of safe, warning and unsafe scan counts per day.',
      }),
    ).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Accessible daily verdict trend values' })).toBeInTheDocument()
  })

  it('can hide series when a metric filter is active', () => {
    const { container } = render(
      <VerdictTrendChart points={twoDays} visibleSeries={['unsafe']} />,
    )
    const polylines = container.querySelectorAll('polyline')
    expect(polylines).toHaveLength(1)
    expect(polylines[0]).toHaveAttribute('stroke', '#b24b44')
  })
})

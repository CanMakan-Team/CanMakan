import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { HoverTip } from '../../shared/ui/HoverTip'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { chartEndLabelIndexes } from './consumerTrendsChartAxis'
import {
  usageStatisticsApiService,
  type UsagePeriodDays,
  type UsageStatistics,
} from './usageStatisticsApiService'

/**
 * UC15 - View Application Usage Statistics (system admin).
 *
 * One page, four colour-coded sections: acquisition and conversion (blue), activity and stickiness
 * (green), retention and churn (amber/red), and engagement and sessions (blue with a warm heatmap).
 * Mirrors the Consumer Trends page structure (page header, period filter, panels). Data currently
 * comes from a mock service; see usageStatisticsApiService for the intended backend contract.
 */

const PERIOD_OPTIONS = [7, 30, 90] as const

const KPI_HELP = {
  newSignups: 'New accounts created during the selected reporting period.',
  dailyActiveUsers: 'Distinct users who scanned in the last 24 hours.',
  weeklyActiveUsers: 'Distinct users who scanned in the last 7 days.',
  monthlyActiveUsers: 'Distinct users who scanned in the last 30 days.',
  stickiness: 'How many monthly users also used the app in the last 24 hours: daily active users ÷ monthly active users (DAU ÷ MAU).',
  averageSession: 'Average time a user spends in one session during the selected period.',
  day1: 'Share of accounts that scanned again at least one day after signing up.',
  day7: 'Share of accounts that scanned again at least seven days after signing up.',
  day30: 'Share of accounts that scanned again at least 30 days after signing up.',
  resurrected: 'Users who scanned in this period after 30 or more days without a scan.',
  churn: 'Share of users who scanned in the previous period but not in this one.',
  inactive30d: 'Accounts with no scan in the last 30 days.',
  sessionsPerUser: 'Average number of sessions per active user in the selected period.',
  activeDaysPerWeek: 'Average distinct days per week that an active user scanned.',
}

// Section colours. Reuse the verdict palette (green/amber/red) and add a blue accent so the four
// groups read distinctly while staying within the admin theme.
const BLUE = '#2f6d9e'
const BLUE_BG = '#eaf1f7'
const GREEN = '#27875b'
const GREEN_BG = '#e7f1ec'
const AMBER = '#d6a12b'
const AMBER_BG = '#f8f0dd'
const RED = '#b24b44'
const RED_BG = '#f6e9e7'

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-SG').format(value)
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const remaining = seconds % 60
  return `${minutes}m ${remaining}s`
}

export function UsageStatisticsPage() {
  const [periodDays, setPeriodDays] = useState<UsagePeriodDays>(7)
  const [data, setData] = useState<UsageStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(undefined)
    try {
      setData(await usageStatisticsApiService.getUsageStatistics(periodDays))
    } catch {
      setErrorMessage('The usage statistics could not be loaded. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [periodDays])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const handleExport = useCallback(() => {
    if (!data) return
    const rows = [
      'Metric,Value',
      `New sign-ups,${data.kpis.newSignups}`,
      `Daily active users,${data.kpis.dailyActiveUsers}`,
      `Stickiness (%),${data.kpis.stickinessPct}`,
      `Average session (seconds),${data.kpis.averageSessionSeconds}`,
      `D1 retention (%),${data.retention.day1Pct}`,
      `D7 retention (%),${data.retention.day7Pct}`,
      `D30 retention (%),${data.retention.day30Pct}`,
      `Churn (%),${data.retention.churnPct}`,
      `Inactive 30 days,${data.retention.inactive30d}`,
    ]
    const url = URL.createObjectURL(
      new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8' }),
    )
    const link = document.createElement('a')
    link.href = url
    link.download = `usage-statistics-${periodDays}d.csv`
    link.click()
    URL.revokeObjectURL(url)
  }, [data, periodDays])

  return (
    <div className="analytics-page usage-statistics-page">
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Application Usage</p>
          <h1>Usage Statistics</h1>
          <p>
            How app users engage with CanMakan: acquisition, activity, retention and sessions across
            the selected reporting period.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics usage-toolbar"
        aria-label="Usage statistics controls"
      >
        <div className="field-group">
          <label htmlFor="usage-period">Reporting period</label>
          <select
            id="usage-period"
            value={periodDays}
            disabled={loading}
            onChange={(event) => setPeriodDays(Number(event.target.value) as UsagePeriodDays)}
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option} value={option}>
                Last {option} days
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="button button--primary"
          onClick={handleExport}
          disabled={!data || loading}
        >
          Export CSV
        </button>
      </section>

      {loading ? (
        <LoadingState label="Loading usage statistics…" />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : data ? (
        <UsageStatisticsResult data={data} />
      ) : (
        <EmptyState
          title="No usage data"
          description="No usage statistics are available yet."
          showMascot={false}
        />
      )}
    </div>
  )
}

function UsageStatisticsResult({ data }: { data: UsageStatistics }) {
  const { kpis, acquisition, activity, retention, engagement } = data
  return (
    <>
      <section className="analytics-summary-grid usage-summary-grid" aria-label="Usage statistics summary">
        <StatCard
          label="New sign-ups"
          value={formatNumber(kpis.newSignups)}
          color={BLUE}
          background={BLUE_BG}
          title={KPI_HELP.newSignups}
        />
        <StatCard
          label="Daily active users"
          value={formatNumber(kpis.dailyActiveUsers)}
          color={GREEN}
          background={GREEN_BG}
          title={KPI_HELP.dailyActiveUsers}
        />
        <StatCard
          label="Stickiness (DAU/MAU)"
          value={`${kpis.stickinessPct}%`}
          color={AMBER}
          background={AMBER_BG}
          title={KPI_HELP.stickiness}
        />
        <StatCard
          label="Avg session"
          value={formatDuration(kpis.averageSessionSeconds)}
          color={RED}
          background={RED_BG}
          title={
            kpis.averageSessionSeconds === 0
              ? `${KPI_HELP.averageSession} No sessions were recorded in this period.`
              : KPI_HELP.averageSession
          }
        />
      </section>

      <SectionPanel accent={BLUE} eyebrow="Acquisition" title="Acquisition & conversion">
        <div className="usage-two-col">
          <div>
            <p className="usage-caption">Daily new registrations</p>
            <MiniBarChart values={acquisition.dailyNewRegistrations} color={BLUE} />
          </div>
          <div>
            <p className="usage-caption">Activation funnel</p>
            <div className="usage-funnel">
              {acquisition.activationFunnel.map((step) => (
                <div key={step.label} className="usage-funnel-row">
                  <p className="usage-funnel-label">
                    {step.label} · {step.percent}%
                  </p>
                  <div className="usage-funnel-track">
                    <span style={{ width: `${step.percent}%`, background: BLUE }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </SectionPanel>

      <SectionPanel accent={GREEN} eyebrow="Activity" title="Activity & stickiness">
        <div className="usage-chip-row">
          <Chip
            label="DAU"
            value={formatNumber(activity.dailyActiveUsers)}
            color={GREEN}
            background={GREEN_BG}
            title={KPI_HELP.dailyActiveUsers}
          />
          <Chip
            label="WAU"
            value={formatNumber(activity.weeklyActiveUsers)}
            color={GREEN}
            background={GREEN_BG}
            title={KPI_HELP.weeklyActiveUsers}
          />
          <Chip
            label="MAU"
            value={formatNumber(activity.monthlyActiveUsers)}
            color={GREEN}
            background={GREEN_BG}
            title={KPI_HELP.monthlyActiveUsers}
          />
          <Chip
            label="Stickiness"
            value={`${activity.stickinessPct}%`}
            color={AMBER}
            background={AMBER_BG}
            title={KPI_HELP.stickiness}
          />
        </div>
        <p className="usage-caption usage-caption--spaced">New vs returning users</p>
        <div className="usage-split-bar" role="img" aria-label={`New ${activity.newUsersPct}%, returning ${activity.returningUsersPct}%`}>
          <span style={{ width: `${activity.newUsersPct}%`, background: BLUE }} />
          <span style={{ width: `${activity.returningUsersPct}%`, background: GREEN }} />
        </div>
        <div className="usage-split-legend">
          <span style={{ color: BLUE }}>New {activity.newUsersPct}%</span>
          <span style={{ color: GREEN }}>Returning {activity.returningUsersPct}%</span>
        </div>
      </SectionPanel>

      <SectionPanel accent={AMBER} eyebrow="Retention" title="Retention & churn">
        <div className="usage-retention-grid">
          <Chip
            label="D1 retention"
            value={`${retention.day1Pct}%`}
            color={AMBER}
            background={AMBER_BG}
            block
            title={KPI_HELP.day1}
          />
          <Chip
            label="D7 retention"
            value={`${retention.day7Pct}%`}
            color={AMBER}
            background={AMBER_BG}
            block
            title={KPI_HELP.day7}
          />
          <Chip
            label="D30 retention"
            value={`${retention.day30Pct}%`}
            color={AMBER}
            background={AMBER_BG}
            block
            title={KPI_HELP.day30}
          />
          <Chip
            label="Resurrected"
            value={formatNumber(retention.resurrectedUsers)}
            color={GREEN}
            background={GREEN_BG}
            block
            title={KPI_HELP.resurrected}
          />
          <Chip
            label="Churn rate"
            value={`${retention.churnPct}%`}
            color={RED}
            background={RED_BG}
            block
            title={KPI_HELP.churn}
          />
          <Chip
            label="Inactive 30d"
            value={formatNumber(retention.inactive30d)}
            hint={`of ${formatNumber(retention.totalUsers)}`}
            color={RED}
            background={RED_BG}
            block
            title={KPI_HELP.inactive30d}
          />
        </div>
      </SectionPanel>

      <SectionPanel accent={BLUE} eyebrow="Engagement" title="Engagement & sessions">
        <div className="usage-chip-row usage-chip-row--tight">
          <Chip
            label="Avg session"
            value={formatDuration(engagement.averageSessionSeconds)}
            color={BLUE}
            background={BLUE_BG}
            title={
              engagement.averageSessionSeconds === 0
                ? `${KPI_HELP.averageSession} No sessions were recorded in this period.`
                : KPI_HELP.averageSession
            }
          />
          <Chip
            label="Sessions / user"
            value={String(engagement.sessionsPerUser)}
            color={BLUE}
            background={BLUE_BG}
            title={KPI_HELP.sessionsPerUser}
          />
          <Chip
            label="Active days / week"
            value={String(engagement.activeDaysPerWeek)}
            color={BLUE}
            background={BLUE_BG}
            title={KPI_HELP.activeDaysPerWeek}
          />
        </div>
        <p className="usage-caption">Activity heatmap · weekday × hour</p>
        <ActivityHeatmap heatmap={engagement.heatmap} />
        <div className="usage-heatmap-legend">
          <LegendDot color={BLUE} label="low" />
          <LegendDot color={AMBER} label="medium" />
          <LegendDot color={RED} label="peak" />
        </div>
      </SectionPanel>
    </>
  )
}

function SectionPanel({
  accent,
  eyebrow,
  title,
  children,
}: {
  accent: string
  eyebrow: string
  title: string
  children: ReactNode
}) {
  return (
    <section className="panel usage-section-panel" style={{ borderTop: `3px solid ${accent}` }}>
      <div className="panel__header">
        <div>
          <p className="eyebrow usage-section-eyebrow" style={{ color: accent }}>{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </div>
      {children}
    </section>
  )
}

function StatCard({
  label,
  value,
  color,
  background,
  title,
}: {
  label: string
  value: string
  color: string
  background: string
  title: string
}) {
  return (
    <HoverTip text={title} className="hover-tip--block">
      <article className="usage-stat-card" style={{ background }}>
        <p className="usage-stat-card__label">{label}</p>
        <strong className="usage-stat-card__value" style={{ color }}>{value}</strong>
      </article>
    </HoverTip>
  )
}

function Chip({
  label,
  value,
  hint,
  color,
  background,
  block,
  title,
}: {
  label: string
  value: string
  hint?: string
  color: string
  background: string
  block?: boolean
  title: string
}) {
  return (
    <HoverTip text={title} className={block ? 'hover-tip--block' : 'hover-tip--chip'}>
      <div className={`usage-chip${block ? ' usage-chip--block' : ''}`} style={{ background }}>
        <p className="usage-chip__label">{label}</p>
        <strong className="usage-chip__value" style={{ color }}>{value}</strong>
        {hint ? <span className="usage-chip__hint">{hint}</span> : null}
      </div>
    </HoverTip>
  )
}

function MiniBarChart({ values, color }: { values: number[]; color: string }) {
  const max = Math.max(1, ...values)
  const labelIndexes = new Set(chartEndLabelIndexes(values.length))
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)
  return (
    <div className="usage-bar-chart">
      <div className="usage-bar-chart__y" aria-hidden="true">
        <span>{max}</span>
        <span>{Math.round(max / 2)}</span>
        <span>0</span>
      </div>
      <div className="usage-bar-chart__plot">
        <div className="usage-bar-chart__grid" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div
          className="usage-bar-chart__bars"
          role="img"
          aria-label={`Daily new registrations over ${values.length} days. Peak ${max}.`}
        >
          {values.map((value, index) => (
            <span
              key={index}
              className="usage-bar-chart__col"
              onMouseEnter={() => setHoveredIndex(index)}
              onMouseLeave={() => setHoveredIndex(null)}
            >
              <span
                className="usage-bar-chart__bar"
                style={{ height: `${Math.max(4, (value / max) * 100)}%`, background: color }}
              />
            </span>
          ))}
        </div>
        {hoveredIndex !== null ? (
          <span className="hover-tip__bubble hover-tip__bubble--chart" role="tooltip">
            Day {hoveredIndex + 1}: {values[hoveredIndex]} registrations
          </span>
        ) : null}
        <div className="usage-bar-chart__x" aria-hidden="true">
          {values.map((_, index) => (
            <span key={index}>{labelIndexes.has(index) ? String(index + 1) : ''}</span>
          ))}
        </div>
      </div>
    </div>
  )
}

function ActivityHeatmap({ heatmap }: { heatmap: number[][] }) {
  const columns = heatmap[0]?.length ?? 12
  return (
    <div
      className="usage-heatmap"
      style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
      role="img"
      aria-label="Activity heatmap by weekday and hour of day."
    >
      {heatmap.flatMap((row, dayIndex) =>
        row.map((intensity, bucketIndex) => (
          <span
            key={`${dayIndex}-${bucketIndex}`}
            style={{
              height: 12,
              borderRadius: 2,
              background: heatColor(intensity),
              opacity: 0.25 + intensity * 0.75,
            }}
          />
        )),
      )}
    </div>
  )
}

function heatColor(intensity: number): string {
  if (intensity > 0.66) return RED
  if (intensity > 0.33) return AMBER
  return BLUE
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <i style={{ width: 10, height: 10, borderRadius: 2, background: color, display: 'inline-block' }} />
      {label}
    </span>
  )
}

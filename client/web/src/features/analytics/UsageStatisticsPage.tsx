import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
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
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 15 - application usage</p>
          <h1>Usage Statistics</h1>
          <p>
            How app users engage with CanMakan: acquisition, activity, retention and sessions across
            the selected reporting period.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics"
        aria-label="Usage statistics controls"
        style={{ display: 'flex', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}
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
          onClick={handleExport}
          disabled={!data || loading}
          style={{
            marginLeft: 'auto',
            background: '#16202e',
            color: '#fff',
            border: 'none',
            borderRadius: 9,
            padding: '10px 18px',
            fontWeight: 600,
            fontSize: '0.85rem',
            cursor: !data || loading ? 'not-allowed' : 'pointer',
            opacity: !data || loading ? 0.5 : 1,
          }}
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
    </>
  )
}

function UsageStatisticsResult({ data }: { data: UsageStatistics }) {
  const { kpis, acquisition, activity, retention, engagement } = data
  return (
    <>
      <section className="summary-grid" aria-label="Usage statistics summary">
        <StatCard label="New sign-ups" value={formatNumber(kpis.newSignups)} color={BLUE} background={BLUE_BG} />
        <StatCard label="Daily active users" value={formatNumber(kpis.dailyActiveUsers)} color={GREEN} background={GREEN_BG} />
        <StatCard label="Stickiness (DAU/MAU)" value={`${kpis.stickinessPct}%`} color={AMBER} background={AMBER_BG} />
        <StatCard label="Avg session" value={formatDuration(kpis.averageSessionSeconds)} color={RED} background={RED_BG} />
      </section>

      <SectionPanel accent={BLUE} eyebrow="Acquisition" title="Acquisition & conversion">
        <div className="usage-two-col" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem' }}>
          <div>
            <p className="usage-caption">Daily new registrations</p>
            <MiniBarChart values={acquisition.dailyNewRegistrations} color={BLUE} />
          </div>
          <div>
            <p className="usage-caption">Activation funnel</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {acquisition.activationFunnel.map((step) => (
                <div
                  key={step.label}
                  style={{
                    background: BLUE_BG,
                    color: BLUE,
                    borderRadius: 8,
                    padding: '5px 10px',
                    width: `${step.percent}%`,
                    minWidth: 120,
                    fontSize: '0.82rem',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {step.label} · {step.percent}%
                </div>
              ))}
            </div>
          </div>
        </div>
      </SectionPanel>

      <SectionPanel accent={GREEN} eyebrow="Activity" title="Activity & stickiness">
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Chip label="DAU" value={formatNumber(activity.dailyActiveUsers)} color={GREEN} background={GREEN_BG} />
          <Chip label="WAU" value={formatNumber(activity.weeklyActiveUsers)} color={GREEN} background={GREEN_BG} />
          <Chip label="MAU" value={formatNumber(activity.monthlyActiveUsers)} color={GREEN} background={GREEN_BG} />
          <Chip label="Stickiness" value={`${activity.stickinessPct}%`} color={AMBER} background={AMBER_BG} />
        </div>
        <p className="usage-caption" style={{ marginTop: '1rem' }}>New vs returning users</p>
        <div style={{ display: 'flex', height: 16, borderRadius: 8, overflow: 'hidden' }}>
          <span style={{ width: `${activity.newUsersPct}%`, background: BLUE }} />
          <span style={{ width: `${activity.returningUsersPct}%`, background: GREEN }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', marginTop: 4 }}>
          <span style={{ color: BLUE }}>New {activity.newUsersPct}%</span>
          <span style={{ color: GREEN }}>Returning {activity.returningUsersPct}%</span>
        </div>
      </SectionPanel>

      <SectionPanel accent={AMBER} eyebrow="Retention" title="Retention & churn">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10 }}>
          <Chip label="D1 retention" value={`${retention.day1Pct}%`} color={AMBER} background={AMBER_BG} block />
          <Chip label="D7 retention" value={`${retention.day7Pct}%`} color={AMBER} background={AMBER_BG} block />
          <Chip label="D30 retention" value={`${retention.day30Pct}%`} color={AMBER} background={AMBER_BG} block />
          <Chip label="Resurrected" value={formatNumber(retention.resurrectedUsers)} color={GREEN} background={GREEN_BG} block />
          <Chip label="Churn rate" value={`${retention.churnPct}%`} color={RED} background={RED_BG} block />
          <Chip
            label="Inactive 30d"
            value={formatNumber(retention.inactive30d)}
            hint={`of ${formatNumber(retention.totalUsers)}`}
            color={RED}
            background={RED_BG}
            block
          />
        </div>
      </SectionPanel>

      <SectionPanel accent={BLUE} eyebrow="Engagement" title="Engagement & sessions">
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: '1rem' }}>
          <Chip label="Avg session" value={formatDuration(engagement.averageSessionSeconds)} color={BLUE} background={BLUE_BG} />
          <Chip label="Sessions / user" value={String(engagement.sessionsPerUser)} color={BLUE} background={BLUE_BG} />
          <Chip label="Active days / week" value={String(engagement.activeDaysPerWeek)} color={BLUE} background={BLUE_BG} />
        </div>
        <p className="usage-caption">Activity heatmap · weekday × hour</p>
        <ActivityHeatmap heatmap={engagement.heatmap} />
        <div style={{ display: 'flex', gap: 14, fontSize: '0.76rem', color: '#6b7772', marginTop: 6 }}>
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
    <section className="panel" style={{ borderTop: `3px solid ${accent}` }}>
      <div className="panel__header">
        <div>
          <p className="eyebrow" style={{ color: accent }}>{eyebrow}</p>
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
}: {
  label: string
  value: string
  color: string
  background: string
}) {
  return (
    <article style={{ background, borderRadius: 14, padding: '16px 18px' }}>
      <div style={{ fontSize: '0.8rem', color }}>{label}</div>
      <div style={{ fontSize: '1.9rem', fontWeight: 800, color, marginTop: 4 }}>{value}</div>
    </article>
  )
}

function Chip({
  label,
  value,
  hint,
  color,
  background,
  block,
}: {
  label: string
  value: string
  hint?: string
  color: string
  background: string
  block?: boolean
}) {
  return (
    <div
      style={{
        flex: block ? undefined : '1 1 90px',
        textAlign: 'center',
        background,
        borderRadius: 10,
        padding: '10px 6px',
        minWidth: 90,
      }}
    >
      <div style={{ fontSize: '0.72rem', color }}>{label}</div>
      <div style={{ fontSize: '1.25rem', fontWeight: 700, color, marginTop: 2 }}>{value}</div>
      {hint && <div style={{ fontSize: '0.7rem', color: '#6b7772', marginTop: 2 }}>{hint}</div>}
    </div>
  )
}

function MiniBarChart({ values, color }: { values: number[]; color: string }) {
  const max = Math.max(1, ...values)
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 64 }} aria-hidden="true">
      {values.map((value, index) => (
        <span
          key={index}
          title={String(value)}
          style={{
            flex: 1,
            height: `${Math.max(6, (value / max) * 100)}%`,
            background: color,
            borderRadius: '3px 3px 0 0',
          }}
        />
      ))}
    </div>
  )
}

function ActivityHeatmap({ heatmap }: { heatmap: number[][] }) {
  const columns = heatmap[0]?.length ?? 12
  return (
    <div
      style={{ display: 'grid', gridTemplateColumns: `repeat(${columns}, 1fr)`, gap: 3 }}
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

import { useState, type ReactNode } from 'react'
import { HoverTip } from '../../../shared/ui/HoverTip'
import { ADMIN_ACCENT } from '../lib/adminAnalyticsPalette'
import { chartEndLabelIndexes } from '../lib/consumerTrendsChartAxis'
import type { UsageStatistics } from '../api/usageStatisticsApiService'

const BLUE = ADMIN_ACCENT.info
const BLUE_BG = ADMIN_ACCENT.infoBg
const GREEN = ADMIN_ACCENT.safe
const GREEN_BG = ADMIN_ACCENT.safeBg
const AMBER = ADMIN_ACCENT.warning
const AMBER_BG = ADMIN_ACCENT.warningBg
const RED = ADMIN_ACCENT.avoid
const RED_BG = ADMIN_ACCENT.avoidBg

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

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-SG').format(value)
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const remaining = seconds % 60
  return `${minutes}m ${remaining}s`
}

export function UsageStatisticsResult({ data }: Readonly<{ data: UsageStatistics }>) {
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
}: Readonly<{
  accent: string
  eyebrow: string
  title: string
  children: ReactNode
}>) {
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
}: Readonly<{
  label: string
  value: string
  color: string
  background: string
  title: string
}>) {
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
}: Readonly<{
  label: string
  value: string
  hint?: string
  color: string
  background: string
  block?: boolean
  title: string
}>) {
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

function MiniBarChart({ values, color }: Readonly<{ values: number[]; color: string }>) {
  const max = Math.max(1, ...values)
  const bars = values.map((count, dayOffset) => ({
    day: dayOffset + 1,
    count,
  }))
  const labelIndexes = new Set(chartEndLabelIndexes(values.length))
  const [hoveredDay, setHoveredDay] = useState<number | null>(null)
  const hoveredBar = bars.find((bar) => bar.day === hoveredDay)
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
          {bars.map((bar) => (
            <span
              key={`registration-day-${bar.day}`}
              className="usage-bar-chart__col"
              onMouseEnter={() => setHoveredDay(bar.day)}
              onMouseLeave={() => setHoveredDay(null)}
            >
              <span
                className="usage-bar-chart__bar"
                style={{ height: `${Math.max(4, (bar.count / max) * 100)}%`, background: color }}
              />
            </span>
          ))}
        </div>
        {hoveredBar ? (
          <span className="hover-tip__bubble hover-tip__bubble--chart" role="tooltip">
            Day {hoveredBar.day}: {hoveredBar.count} registrations
          </span>
        ) : null}
        <div className="usage-bar-chart__x" aria-hidden="true">
          {bars.map((bar) => (
            <span key={`registration-axis-${bar.day}`}>
              {labelIndexes.has(bar.day - 1) ? String(bar.day) : ''}
            </span>
          ))}
        </div>
      </div>
    </div>
  )
}

function ActivityHeatmap({ heatmap }: Readonly<{ heatmap: number[][] }>) {
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

function LegendDot({ color, label }: Readonly<{ color: string; label: string }>) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <i style={{ width: 10, height: 10, borderRadius: 2, background: color, display: 'inline-block' }} />
      {label}
    </span>
  )
}

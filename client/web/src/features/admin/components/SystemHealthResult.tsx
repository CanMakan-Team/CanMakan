import { useMemo, useState, type ReactNode } from 'react'
import { HoverTip } from '../../../shared/ui/HoverTip'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { ADMIN_ACCENT } from '../../analytics/lib/adminAnalyticsPalette'
import {
  type AuditEntry,
  type HealthWindowHours,
  type SystemHealth,
} from '../api/systemHealthApiService'

const BLUE = ADMIN_ACCENT.info
const BLUE_BG = ADMIN_ACCENT.infoBg
const GREEN = ADMIN_ACCENT.safe
const GREEN_BG = ADMIN_ACCENT.safeBg
const AMBER = ADMIN_ACCENT.warning
const AMBER_BG = ADMIN_ACCENT.warningBg
const RED = ADMIN_ACCENT.avoid
const RED_BG = ADMIN_ACCENT.avoidBg

const COMPONENT_STATUS_HELP: Record<string, { up: string; down: string }> = {
  db: {
    up: 'UP means the database accepted a connectivity check and is reachable.',
    down: 'DOWN means the database did not respond to the connectivity check.',
  },
  diskSpace: {
    up: 'UP means the host still has enough free disk space for the application to run.',
    down: 'DOWN means free disk space has fallen below the healthy threshold.',
  },
  application: {
    up: 'UP means the application process is running and reporting as healthy.',
    down: 'DOWN means the application process is not reporting as healthy.',
  },
}

const AI_METRIC_HELP = {
  tier3Rate: 'Share of AI calls in this window that used the most expensive model tier (Tier 3).',
  averageLatency: 'Average time to complete an AI call in this reporting window.',
  maxLatency: 'Longest AI call duration recorded in this reporting window.',
  totalCalls: 'Total AI executions recorded in this reporting window.',
}

function componentStatusHelp(name: string, up: boolean): string {
  const specific = COMPONENT_STATUS_HELP[name]
  if (specific) {
    return up ? specific.up : specific.down
  }
  return up
    ? 'UP means this component passed its health check.'
    : 'DOWN means this component failed its health check.'
}

function isHealthy(status: string): boolean {
  return status.toUpperCase() === 'UP'
}

function formatLatency(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${Math.round(ms)}ms`
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime())
    ? timestamp
    : date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function SystemHealthResult({
  data,
  hours,
}: Readonly<{
  data: SystemHealth
  hours: HealthWindowHours
}>) {
  const healthy = isHealthy(data.overallStatus)
  const { ai, auditTrail, scanQuality } = data
  const chartEmpty = ai.totalCalls === 0 || ai.latencyTrend.length === 0

  return (
    <>
      <section
        className={`health-overall ${healthy ? 'health-overall--up' : 'health-overall--down'}`}
        aria-label="Overall status"
      >
        <span className="health-overall__mark" aria-hidden="true">{healthy ? '✓' : '!'}</span>
        <div>
          <div className="health-overall__status">Status: {data.overallStatus}</div>
          <div className="health-overall__hint">From database and disk probe</div>
        </div>
      </section>

      <SectionPanel accent={GREEN} title="Component health" caption="Source: database connectivity and disk space">
        {data.components.length === 0 ? (
          <p className="health-muted">No component details were reported.</p>
        ) : (
          <div className="health-component-grid">
            {data.components.map((component) => {
              const up = isHealthy(component.status)
              return (
                <article key={component.name} className="health-component-card">
                  <span
                    className={`health-component-card__dot${up ? ' is-up' : ' is-down'}`}
                    aria-hidden="true"
                  />
                  <span className="health-component-card__name">{component.name}</span>
                  <HoverTip text={componentStatusHelp(component.name, up)}>
                    <StatusBadge
                      status={up ? 'COMPLETE' : 'UNSAFE'}
                      label={component.status}
                    />
                  </HoverTip>
                </article>
              )
            })}
          </div>
        )}
      </SectionPanel>

      <SectionPanel accent={BLUE} title="AI execution monitoring" caption="Source: AI execution logs">
        <div className="health-chip-grid">
          <Chip
            label="Tier-3 rate"
            value={`${ai.tier3RatePct}%`}
            color={BLUE}
            background={BLUE_BG}
            title={AI_METRIC_HELP.tier3Rate}
          />
          <Chip
            label="Avg latency"
            value={formatLatency(ai.averageLatencyMs)}
            color={BLUE}
            background={BLUE_BG}
            title={AI_METRIC_HELP.averageLatency}
          />
          <Chip
            label="Max latency"
            value={formatLatency(ai.maxLatencyMs)}
            color={AMBER}
            background={AMBER_BG}
            title={AI_METRIC_HELP.maxLatency}
          />
          <Chip
            label="AI calls"
            value={ai.totalCalls.toLocaleString()}
            color={BLUE}
            background={BLUE_BG}
            title={AI_METRIC_HELP.totalCalls}
          />
        </div>
        <p className="usage-caption">Latency trend</p>
        <div className={`health-chart${chartEmpty ? ' health-chart--empty' : ''}`}>
          {chartEmpty ? (
            <p className="health-muted">No AI calls in this window.</p>
          ) : (
            <MiniAreaChart
              values={ai.latencyTrend}
              hours={hours}
              generatedAt={data.generatedAt}
              color={BLUE}
              background={BLUE_BG}
            />
          )}
        </div>
        <p className="usage-caption">Slowest calls</p>
        {ai.slowestCalls.length === 0 ? (
          <p className="health-muted">No AI calls in this window.</p>
        ) : (
          <div className="health-slow-list">
            {ai.slowestCalls.map((call) => (
              <div key={call.scanId} className="health-slow-row">
                <span className="health-slow-row__scan">scan {call.scanId}</span>
                <span className="health-slow-row__tier">{call.tier}</span>
                <span className={`health-slow-row__ms${call.latencyMs >= 1000 ? ' is-slow' : ''}`}>
                  {call.latencyMs.toLocaleString()}ms
                </span>
              </div>
            ))}
          </div>
        )}
      </SectionPanel>

      <SectionPanel accent={AMBER} title="Admin activity audit" caption="Source: admin audit logs">
        <AuditTrailSection entries={auditTrail} />
      </SectionPanel>

      <SectionPanel accent={RED} title="Scan data quality" caption="Source: scan records (incomplete data and verdict mix)">
        <div className="health-chip-grid">
          <Chip label="Incomplete data" value={`${scanQuality.incompleteDataPct}%`} color={RED} background={RED_BG} />
          <Chip label="Warnings" value={`${scanQuality.warningPct}%`} color={AMBER} background={AMBER_BG} />
          <Chip label="Unsafe" value={`${scanQuality.unsafePct}%`} color={RED} background={RED_BG} />
          <Chip label="Safe" value={`${scanQuality.safePct}%`} color={GREEN} background={GREEN_BG} />
        </div>
        <div className="health-mix-bar" aria-hidden="true">
          <span style={{ width: `${scanQuality.safePct}%`, background: GREEN }} />
          <span style={{ width: `${scanQuality.warningPct}%`, background: AMBER }} />
          <span style={{ width: `${scanQuality.unsafePct}%`, background: RED }} />
        </div>
        <div className="health-muted">
          {scanQuality.totalScans.toLocaleString()} scans in window
        </div>
      </SectionPanel>
    </>
  )
}

function AuditTrailSection({ entries }: Readonly<{ entries: AuditEntry[] }>) {
  const [adminFilter, setAdminFilter] = useState('ALL')
  const [actionFilter, setActionFilter] = useState('ALL')

  const admins = useMemo(
    () => Array.from(new Set(entries.map((entry) => entry.admin))).sort((left, right) => left.localeCompare(right)),
    [entries],
  )
  const actions = useMemo(
    () => Array.from(new Set(entries.map((entry) => entry.action))).sort((left, right) => left.localeCompare(right)),
    [entries],
  )
  const visibleEntries = entries.filter((entry) => (
    (adminFilter === 'ALL' || entry.admin === adminFilter)
    && (actionFilter === 'ALL' || entry.action === actionFilter)
  ))

  if (entries.length === 0) {
    return <p className="health-muted">No admin actions recorded.</p>
  }

  return (
    <>
      <div className="filter-bar filter-bar--system health-audit-filters" aria-label="Audit log filters">
        <div className="field-group">
          <label htmlFor="audit-admin">Admin user</label>
          <select
            id="audit-admin"
            value={adminFilter}
            onChange={(event) => setAdminFilter(event.target.value)}
          >
            <option value="ALL">All admins</option>
            {admins.map((admin) => (
              <option key={admin} value={admin}>{admin}</option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="audit-action">Event type</label>
          <select
            id="audit-action"
            value={actionFilter}
            onChange={(event) => setActionFilter(event.target.value)}
          >
            <option value="ALL">All events</option>
            {actions.map((action) => (
              <option key={action} value={action}>{action}</option>
            ))}
          </select>
        </div>
      </div>
      {visibleEntries.length === 0 ? (
        <p className="health-muted">No admin actions match the selected filters.</p>
      ) : (
        <div className="responsive-table">
          <table className="data-table health-audit-table">
            <caption className="sr-only">Admin activity audit</caption>
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>User</th>
                <th>Action</th>
                <th>Target</th>
              </tr>
            </thead>
            <tbody>
              {visibleEntries.map((entry, index) => (
                <tr key={`${entry.timestamp}-${entry.admin}-${index}`}>
                  <td>
                    <time dateTime={entry.timestamp}>{formatTime(entry.timestamp)}</time>
                  </td>
                  <th scope="row">{entry.admin}</th>
                  <td>
                    <span className="health-audit-action">{entry.action}</span>
                  </td>
                  <td>
                    {entry.target}
                    {entry.ipAddress ? ` · ${entry.ipAddress}` : ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function SectionPanel({
  accent,
  title,
  caption,
  children,
}: Readonly<{
  accent: string
  title: string
  caption: string
  children: ReactNode
}>) {
  return (
    <section className="panel health-section" style={{ borderTop: `3px solid ${accent}` }}>
      <div className="panel__header">
        <div>
          <p className="health-section__source">{caption}</p>
          <h2>{title}</h2>
        </div>
      </div>
      {children}
    </section>
  )
}

function Chip({
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
  title?: string
}>) {
  const card = (
    <div className="health-chip" style={{ background }}>
      <div className="health-chip__label">{label}</div>
      <div className="health-chip__value" style={{ color }}>{value}</div>
    </div>
  )
  if (!title) {
    return card
  }
  return (
    <HoverTip text={title} className="hover-tip--block">
      {card}
    </HoverTip>
  )
}

function formatTrendXLabel(date: Date, hours: HealthWindowHours): string {
  if (hours <= 24) {
    return date.toLocaleString('en-SG', {
      timeZone: 'Asia/Singapore',
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    })
  }
  return date.toLocaleString('en-SG', {
    timeZone: 'Asia/Singapore',
    day: 'numeric',
    month: 'short',
  })
}

function trendPointTime(
  generatedAt: string,
  hours: HealthWindowHours,
  index: number,
  count: number,
): Date {
  const end = new Date(generatedAt)
  const safeEnd = Number.isNaN(end.getTime()) ? new Date() : end
  const spanMs = hours * 3_600_000
  if (count <= 1) {
    return safeEnd
  }
  return new Date(safeEnd.getTime() - spanMs + (spanMs * index) / (count - 1))
}

function trendAxisAnchor(
  labelIndex: number,
  labelCount: number,
): 'start' | 'middle' | 'end' {
  if (labelIndex === 0) {
    return 'start'
  }
  if (labelIndex === labelCount - 1) {
    return 'end'
  }
  return 'middle'
}

function MiniAreaChart({
  values,
  hours,
  generatedAt,
  color,
  background,
}: Readonly<{
  values: number[]
  hours: HealthWindowHours
  generatedAt: string
  color: string
  background: string
}>) {
  const width = 480
  const height = 92
  const padLeft = 36
  const padRight = 10
  const padTop = 8
  const padBottom = 18
  const plotWidth = width - padLeft - padRight
  const plotHeight = height - padTop - padBottom
  const max = Math.max(1, ...values)
  const count = values.length
  const pointAt = (index: number): number => (
    padLeft + (count <= 1 ? plotWidth / 2 : (plotWidth * index) / (count - 1))
  )
  const yOf = (value: number): number => padTop + plotHeight - (value / max) * plotHeight
  const line = values.map((value, index) => `${pointAt(index)},${yOf(value)}`).join(' ')
  const area = `${pointAt(0)},${padTop + plotHeight} ${line} ${pointAt(count - 1)},${padTop + plotHeight}`
  const yTicks = [max, 0]
  const baseline = padTop + plotHeight
  let xIndexes = [0]
  if (count === 2) {
    xIndexes = [0, count - 1]
  } else if (count > 2) {
    xIndexes = [0, Math.floor((count - 1) / 2), count - 1]
  }

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="health-chart__svg" role="img" aria-label="AI call latency trend.">
      {yTicks.map((tick) => (
        <g key={tick}>
          <line
            x1={padLeft}
            y1={yOf(tick)}
            x2={padLeft + plotWidth}
            y2={yOf(tick)}
            className="health-chart-grid"
          />
          <text
            x={padLeft - 5}
            y={yOf(tick) + 3}
            textAnchor="end"
            className="health-chart-axis"
          >
            {formatLatency(tick)}
          </text>
        </g>
      ))}
      <line x1={padLeft} y1={padTop} x2={padLeft} y2={baseline} className="health-chart-axis-line" />
      <line x1={padLeft} y1={baseline} x2={padLeft + plotWidth} y2={baseline} className="health-chart-axis-line" />
      <polygon points={area} fill={background} stroke="none" />
      <polyline points={line} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      {xIndexes.map((index, labelIndex) => (
        <text
          key={index}
          x={pointAt(index)}
          y={height - 3}
          textAnchor={trendAxisAnchor(labelIndex, xIndexes.length)}
          className="health-chart-axis"
        >
          {formatTrendXLabel(trendPointTime(generatedAt, hours, index, count), hours)}
        </text>
      ))}
    </svg>
  )
}

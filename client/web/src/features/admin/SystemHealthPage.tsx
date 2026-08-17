import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import {
  systemHealthApiService,
  type HealthWindowHours,
  type SystemHealth,
} from './systemHealthApiService'

/**
 * UC16 - View System Health Logs (system admin).
 *
 * Four colour-coded sections over data that already exists: component health (green, from Actuator),
 * AI execution monitoring (blue, from ai_execution_logs), admin activity audit (amber, from
 * admin_audit_logs), and scan data quality (red, from scans). Mirrors the other admin analytics
 * pages (page header, period filter, panels).
 */

const WINDOW_OPTIONS: { hours: HealthWindowHours; label: string }[] = [
  { hours: 24, label: 'Last 24 hours' },
  { hours: 168, label: 'Last 7 days' },
  { hours: 720, label: 'Last 30 days' },
]

const BLUE = '#2f6d9e'
const BLUE_BG = '#eaf1f7'
const GREEN = '#27875b'
const GREEN_BG = '#e7f1ec'
const AMBER = '#d6a12b'
const AMBER_BG = '#f8f0dd'
const RED = '#b24b44'
const RED_BG = '#f6e9e7'

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

export function SystemHealthPage() {
  const [hours, setHours] = useState<HealthWindowHours>(24)
  const [data, setData] = useState<SystemHealth | null>(null)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(undefined)
    try {
      setData(await systemHealthApiService.getSystemHealth(hours))
    } catch {
      setErrorMessage('The system health snapshot could not be loaded. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [hours])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 16 - system admin</p>
          <h1>System Health</h1>
          <p>
            Component status, AI execution monitoring, the admin activity trail, and scan data
            quality - assembled from Actuator and existing logs.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics"
        aria-label="System health controls"
        style={{ display: 'flex', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}
      >
        <div className="field-group">
          <label htmlFor="health-window">Reporting window</label>
          <select
            id="health-window"
            value={hours}
            disabled={loading}
            onChange={(event) => setHours(Number(event.target.value) as HealthWindowHours)}
          >
            {WINDOW_OPTIONS.map((option) => (
              <option key={option.hours} value={option.hours}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          onClick={load}
          disabled={loading}
          style={{
            marginLeft: 'auto',
            background: '#16202e',
            color: '#fff',
            border: 'none',
            borderRadius: 9,
            padding: '10px 18px',
            fontWeight: 600,
            fontSize: '0.85rem',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.5 : 1,
          }}
        >
          Refresh
        </button>
      </section>

      {loading ? (
        <LoadingState label="Loading system health…" />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : data ? (
        <SystemHealthResult data={data} />
      ) : (
        <EmptyState title="No health data" description="No system health data is available yet." />
      )}
    </>
  )
}

function SystemHealthResult({ data }: { data: SystemHealth }) {
  const healthy = isHealthy(data.overallStatus)
  const { ai, auditTrail, scanQuality } = data
  return (
    <>
      <section
        aria-label="Overall status"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          background: healthy ? GREEN_BG : RED_BG,
          borderRadius: 14,
          padding: '14px 16px',
        }}
      >
        <span style={{ fontSize: '1.4rem', color: healthy ? GREEN : RED }}>{healthy ? '✓' : '!'}</span>
        <div>
          <div style={{ fontSize: '1rem', color: healthy ? GREEN : RED }}>Status: {data.overallStatus}</div>
          <div style={{ fontSize: '0.8rem', color: '#6b7772' }}>From database and disk probe</div>
        </div>
      </section>

      <SectionPanel accent={GREEN} title="Component health" caption="source: database connectivity + disk space probe">
        {data.components.length === 0 ? (
          <p style={{ fontSize: '0.85rem', color: '#6b7772' }}>No component details were reported.</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 8 }}>
            {data.components.map((component) => {
              const up = isHealthy(component.status)
              return (
                <div
                  key={component.name}
                  style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid #e7e6df', borderRadius: 10, padding: '8px 10px', fontSize: '0.82rem' }}
                >
                  <span style={{ width: 9, height: 9, borderRadius: '50%', background: up ? GREEN : RED }} />
                  <span style={{ flex: 1 }}>{component.name}</span>
                  <span style={{ color: up ? GREEN : RED }}>{component.status}</span>
                </div>
              )
            })}
          </div>
        )}
      </SectionPanel>

      <SectionPanel accent={BLUE} title="AI execution monitoring" caption="source: ai_execution_logs">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(0, 1fr))', gap: 8, marginBottom: '1rem' }}>
          <Chip label="Tier-3 rate" value={`${ai.tier3RatePct}%`} color={BLUE} background={BLUE_BG} />
          <Chip label="Avg latency" value={formatLatency(ai.averageLatencyMs)} color={BLUE} background={BLUE_BG} />
          <Chip label="Max latency" value={formatLatency(ai.maxLatencyMs)} color={AMBER} background={AMBER_BG} />
          <Chip label="AI calls" value={ai.totalCalls.toLocaleString()} color={BLUE} background={BLUE_BG} />
        </div>
        <p className="usage-caption">Latency trend</p>
        <MiniAreaChart values={ai.latencyTrend} color={BLUE} background={BLUE_BG} />
        <p className="usage-caption" style={{ marginTop: '0.9rem' }}>Slowest calls</p>
        {ai.slowestCalls.length === 0 ? (
          <p style={{ fontSize: '0.82rem', color: '#6b7772' }}>No AI calls in this window.</p>
        ) : (
          <div style={{ fontFamily: 'ui-monospace, monospace', fontSize: '0.78rem', display: 'flex', flexDirection: 'column', gap: 4 }}>
            {ai.slowestCalls.map((call) => (
              <div key={call.scanId} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span style={{ color: '#6b7772', flex: '0 0 70px' }}>scan {call.scanId}</span>
                <span style={{ background: BLUE_BG, color: BLUE, borderRadius: 4, padding: '1px 6px' }}>{call.tier}</span>
                <span style={{ flex: 1, textAlign: 'right', color: call.latencyMs >= 1000 ? AMBER : '#6b7772' }}>
                  {call.latencyMs.toLocaleString()}ms
                </span>
              </div>
            ))}
          </div>
        )}
      </SectionPanel>

      <SectionPanel accent={AMBER} title="Admin activity audit" caption="source: admin_audit_logs">
        {auditTrail.length === 0 ? (
          <p style={{ fontSize: '0.82rem', color: '#6b7772' }}>No admin actions recorded.</p>
        ) : (
          <div style={{ fontFamily: 'ui-monospace, monospace', fontSize: '0.78rem', display: 'flex', flexDirection: 'column', gap: 4 }}>
            {auditTrail.map((entry, index) => (
              <div key={index} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span style={{ color: '#6b7772', flex: '0 0 62px' }}>{formatTime(entry.timestamp)}</span>
                <span style={{ color: '#4a5450', flex: '0 0 150px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{entry.admin}</span>
                <span style={{ background: AMBER_BG, color: AMBER, borderRadius: 4, padding: '1px 6px' }}>{entry.action}</span>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {entry.target}
                  {entry.ipAddress ? ` · ${entry.ipAddress}` : ''}
                </span>
              </div>
            ))}
          </div>
        )}
      </SectionPanel>

      <SectionPanel accent={RED} title="Scan data quality" caption="source: scans (INCOMPLETE_DATA / verdict mix)">
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
          <Chip label="Incomplete data" value={`${scanQuality.incompleteDataPct}%`} color={RED} background={RED_BG} />
          <Chip label="Warnings" value={`${scanQuality.warningPct}%`} color={AMBER} background={AMBER_BG} />
          <Chip label="Unsafe" value={`${scanQuality.unsafePct}%`} color={RED} background={RED_BG} />
          <Chip label="Safe" value={`${scanQuality.safePct}%`} color={GREEN} background={GREEN_BG} />
        </div>
        <div style={{ display: 'flex', height: 16, borderRadius: 8, overflow: 'hidden' }}>
          <span style={{ width: `${scanQuality.safePct}%`, background: GREEN }} />
          <span style={{ width: `${scanQuality.warningPct}%`, background: AMBER }} />
          <span style={{ width: `${scanQuality.unsafePct}%`, background: RED }} />
        </div>
        <div style={{ fontSize: '0.78rem', color: '#6b7772', marginTop: 6 }}>
          {scanQuality.totalScans.toLocaleString()} scans in window
        </div>
      </SectionPanel>
    </>
  )
}

function SectionPanel({
  accent,
  title,
  caption,
  children,
}: {
  accent: string
  title: string
  caption: string
  children: ReactNode
}) {
  return (
    <section className="panel" style={{ borderTop: `3px solid ${accent}` }}>
      <div className="panel__header">
        <div>
          <p className="eyebrow" style={{ color: accent }}>{caption}</p>
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
}: {
  label: string
  value: string
  color: string
  background: string
}) {
  return (
    <div style={{ flex: '1 1 90px', textAlign: 'center', background, borderRadius: 10, padding: '10px 6px', minWidth: 90 }}>
      <div style={{ fontSize: '0.72rem', color }}>{label}</div>
      <div style={{ fontSize: '1.25rem', fontWeight: 700, color, marginTop: 2 }}>{value}</div>
    </div>
  )
}

function MiniAreaChart({ values, color, background }: { values: number[]; color: string; background: string }) {
  const width = 480
  const height = 64
  const max = Math.max(1, ...values)
  const count = values.length
  const pointAt = (index: number): number => (count <= 1 ? width / 2 : (width * index) / (count - 1))
  const yOf = (value: number): number => height - (value / max) * (height - 8) - 4
  const line = values.map((value, index) => `${pointAt(index)},${yOf(value)}`).join(' ')
  const area = `${pointAt(0)},${height} ${line} ${pointAt(count - 1)},${height}`
  return (
    <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', height: 'auto' }} role="img" aria-label="AI call latency trend.">
      <polygon points={area} fill={background} stroke="none" />
      <polyline points={line} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

import { apiRequest, useMockApi } from '../../shared/api/apiClient'

/**
 * UC16 - View System Health Logs (system admin).
 *
 * Contract for the system-health snapshot: component status (Actuator), AI execution monitoring
 * (ai_execution_logs), the admin activity trail (admin_audit_logs), and scan data quality (scans).
 * Uses a deterministic mock under `useMockApi`, otherwise calls `/api/admin/system-health`.
 */

export type HealthWindowHours = 24 | 168 | 720

export interface ComponentHealth {
  name: string
  status: string
}

export interface SlowCall {
  scanId: number
  tier: string
  latencyMs: number
}

export interface AiExecutionHealth {
  tier3RatePct: number
  averageLatencyMs: number
  maxLatencyMs: number
  totalCalls: number
  latencyTrend: number[]
  slowestCalls: SlowCall[]
}

export interface AuditEntry {
  timestamp: string
  admin: string
  action: string
  target: string
  ipAddress: string
}

export interface ScanDataQuality {
  incompleteDataPct: number
  safePct: number
  warningPct: number
  unsafePct: number
  totalScans: number
}

export interface SystemHealth {
  generatedAt: string
  overallStatus: string
  components: ComponentHealth[]
  ai: AiExecutionHealth
  auditTrail: AuditEntry[]
  scanQuality: ScanDataQuality
}

export const systemHealthEndpoint = '/api/admin/system-health'

function buildMockSystemHealth(hours: number): SystemHealth {
  const scale = hours / 24
  const now = Date.now()
  return {
    generatedAt: new Date().toISOString(),
    overallStatus: 'UP',
    components: [
      { name: 'db', status: 'UP' },
      { name: 'diskSpace', status: 'UP' },
      { name: 'application', status: 'UP' },
    ],
    ai: {
      tier3RatePct: 12,
      averageLatencyMs: 640,
      maxLatencyMs: 2108,
      totalCalls: Math.round(148 * scale),
      latencyTrend: Array.from({ length: 12 }, (_, index) =>
        Math.round(500 + 260 * Math.sin(index / 2) + (index % 3) * 40),
      ),
      slowestCalls: [
        { scanId: 8421, tier: 'TIER_3_LLM', latencyMs: 2108 },
        { scanId: 8390, tier: 'TIER_3_LLM', latencyMs: 1740 },
        { scanId: 8377, tier: 'TIER_3_LLM', latencyMs: 1205 },
      ],
    },
    auditTrail: [
      { timestamp: new Date(now - 240000).toISOString(), admin: 'admin1@canmakan.com', action: 'SUSPEND', target: 'user 42', ipAddress: '203.0.113.9' },
      { timestamp: new Date(now - 660000).toISOString(), admin: 'sysadmin@canmakan.com', action: 'REACTIVATE', target: 'user 17', ipAddress: '203.0.113.4' },
      { timestamp: new Date(now - 2400000).toISOString(), admin: 'admin2@canmakan.com', action: 'VIEW', target: 'consumer-trends export', ipAddress: '203.0.113.7' },
    ],
    scanQuality: {
      incompleteDataPct: 6,
      safePct: 66,
      warningPct: 28,
      unsafePct: 6,
      totalScans: Math.round(320 * scale),
    },
  }
}

export const systemHealthApiService = {
  getSystemHealth(hours: HealthWindowHours = 24): Promise<SystemHealth> {
    if (useMockApi) return Promise.resolve(buildMockSystemHealth(hours))
    return apiRequest<SystemHealth>(`${systemHealthEndpoint}?hours=${hours}`)
  },
}

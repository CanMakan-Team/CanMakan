import { describe, expect, it } from 'vitest'
import {
  displayScanField,
  formatRelativeScanTime,
  hasScanFieldValue,
} from '../../../features/family/lib/scanHistoryDisplay'

describe('scanHistoryDisplay', () => {
  it('formats relative scan times with a consistent "at {time}" shape', () => {
    const now = Date.parse('2026-08-18T15:00:00+08:00')

    expect(formatRelativeScanTime('2026-08-18T12:04:30+08:00', now).label).toMatch(
      /^Today at /,
    )
    expect(formatRelativeScanTime('2026-08-17T12:04:30+08:00', now).label).toMatch(
      /^Yesterday at /,
    )
    expect(formatRelativeScanTime('2026-08-15T12:04:30+08:00', now).label).toMatch(
      /^3 days ago at /,
    )
    expect(formatRelativeScanTime('2026-08-01T12:04:30+08:00', now).label).toMatch(
      /^17 days ago at /,
    )
    expect(
      formatRelativeScanTime('2026-08-18T12:04:30+08:00', now).absolute,
    ).toContain('2026')
  })

  it('treats blank ingredient fields as empty', () => {
    expect(hasScanFieldValue('')).toBe(false)
    expect(hasScanFieldValue('   ')).toBe(false)
    expect(hasScanFieldValue('Peanut')).toBe(true)
    expect(displayScanField('')).toBe('—')
    expect(displayScanField('', 'None flagged')).toBe('None flagged')
    expect(displayScanField(' Milk ')).toBe('Milk')
  })
})

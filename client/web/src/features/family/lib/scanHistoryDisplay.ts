/**
 * Formats a scan timestamp for skim-friendly history lists.
 * Every label uses the same "{when} at {time}" shape; the full absolute
 * stamp stays on title/tooltip.
 */
export function formatRelativeScanTime(
  iso: string,
  nowMs: number = Date.now(),
): { label: string; absolute: string } {
  const parsed = Date.parse(iso)
  if (Number.isNaN(parsed)) {
    return { label: iso, absolute: iso }
  }

  const absolute = new Intl.DateTimeFormat('en-SG', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(parsed)

  const time = new Intl.DateTimeFormat('en-SG', {
    timeStyle: 'short',
  }).format(parsed)

  const dayMs = 86_400_000
  const startOfDay = (ms: number) => {
    const date = new Date(ms)
    date.setHours(0, 0, 0, 0)
    return date.getTime()
  }
  const dayDiff = Math.round((startOfDay(nowMs) - startOfDay(parsed)) / dayMs)

  if (dayDiff === 0) {
    return { label: `Today at ${time}`, absolute }
  }
  if (dayDiff === 1) {
    return { label: `Yesterday at ${time}`, absolute }
  }
  if (dayDiff > 1) {
    return { label: `${dayDiff} days ago at ${time}`, absolute }
  }

  // Future timestamps (clock skew / timezone edge): still keep the same shape.
  const date = new Intl.DateTimeFormat('en-SG', {
    dateStyle: 'medium',
  }).format(parsed)
  return { label: `${date} at ${time}`, absolute }
}

export function hasScanFieldValue(value: string | undefined | null): boolean {
  return Boolean(value && value.trim())
}

export function displayScanField(
  value: string | undefined | null,
  emptyLabel = '—',
): string {
  const trimmed = value?.trim()
  return trimmed ? trimmed : emptyLabel
}

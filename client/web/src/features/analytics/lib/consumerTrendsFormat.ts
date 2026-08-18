const SINGAPORE_MIDNIGHT = (date: string) => new Date(`${date}T00:00:00+08:00`)

export function formatDate(date: string): string {
  return new Intl.DateTimeFormat('en-SG', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'Asia/Singapore',
  }).format(SINGAPORE_MIDNIGHT(date))
}

export function formatShortDate(date: string): string {
  return new Intl.DateTimeFormat('en-SG', {
    day: 'numeric',
    month: 'short',
    timeZone: 'Asia/Singapore',
  }).format(SINGAPORE_MIDNIGHT(date))
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-SG').format(value)
}

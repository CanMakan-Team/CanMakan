function parseFeedbackTimestamp(value: string): Date {
  if (/([zZ]|[+-]\d{2}:\d{2})$/u.test(value)) {
    return new Date(value)
  }
  return new Date(`${value}+08:00`)
}

function singaporeDayKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Singapore',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function formatExactCreatedAt(value: string): string {
  return parseFeedbackTimestamp(value).toLocaleString('en-SG', {
    timeZone: 'Asia/Singapore',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    hour12: true,
  })
}

export function formatRelativeCreatedAt(value: string, now = new Date()): string {
  const date = parseFeedbackTimestamp(value)
  const today = new Date(`${singaporeDayKey(now)}T00:00:00+08:00`)
  const thatDay = new Date(`${singaporeDayKey(date)}T00:00:00+08:00`)
  const diffDays = Math.round((today.getTime() - thatDay.getTime()) / 86_400_000)
  const time = date.toLocaleString('en-SG', {
    timeZone: 'Asia/Singapore',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  })

  if (diffDays === 0) return `Today at ${time}`
  if (diffDays === 1) return `Yesterday at ${time}`
  if (diffDays > 1 && diffDays < 7) return `${diffDays} days ago`
  return date.toLocaleString('en-SG', {
    timeZone: 'Asia/Singapore',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

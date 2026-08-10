import type {
  DataCompleteness,
  Verdict,
} from '../api/types'

type Status =
  | Verdict
  | DataCompleteness
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'ACTIVE_PROFILE'

export function StatusBadge({
  status,
  label = status.replaceAll('_', ' '),
}: {
  status: Status
  label?: string
}) {
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}`}>
      {label}
    </span>
  )
}

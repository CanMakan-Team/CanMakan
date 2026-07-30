import type {
  AccountStatus,
  DataCompleteness,
  Verdict,
} from '../api/types'

type Status = Verdict | DataCompleteness | AccountStatus | 'ACTIVE_PROFILE'

export function StatusBadge({ status }: { status: Status }) {
  const label = status.replaceAll('_', ' ')
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}`}>
      {label}
    </span>
  )
}

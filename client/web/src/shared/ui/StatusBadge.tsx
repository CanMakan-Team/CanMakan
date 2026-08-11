import type {
  AccountStatus,
  DataCompleteness,
  RestrictionCellStatus,
} from '../api/types'

type Status = RestrictionCellStatus | DataCompleteness | AccountStatus | 'ACTIVE_PROFILE'

export function StatusBadge({ status }: { status: Status }) {
  const label = status.replaceAll('_', ' ')
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}`}>
      {label}
    </span>
  )
}

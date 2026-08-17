import type {
  DataCompleteness,
  RestrictionCellStatus,
} from '../api/types'

type Status =
  | RestrictionCellStatus
  | DataCompleteness
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'ACTIVE_PROFILE'

export function StatusBadge({
  status,
  label = status.replaceAll('_', ' '),
  tone,
}: {
  status: Status
  label?: string
  tone?: 'severe' | 'caution' | 'preference'
}) {
  const toneClass = tone ? ` status-badge--tone-${tone}` : ''
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}${toneClass}`}>
      {label}
    </span>
  )
}

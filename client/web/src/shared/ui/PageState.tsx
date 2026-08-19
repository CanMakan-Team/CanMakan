import type { ReactNode } from 'react'
import { CanMakanMascot, type CanMakanMascotPose } from './CanMakanMascot'

export function LoadingState({ label = 'Loading information…' }: { label?: string }) {
  return (
    <div className="page-state" role="status">
      <span className="spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  )
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry?: () => void
}) {
  return (
    <div className="page-state page-state--error" role="alert">
      <strong>We could not load this information.</strong>
      <p>{message}</p>
      {onRetry && (
        <button className="button button--secondary" type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  pose = 'wave',
  showMascot = true,
  icon,
  action,
}: {
  title: string
  description: string
  pose?: CanMakanMascotPose
  showMascot?: boolean
  icon?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="page-state">
      {showMascot ? <CanMakanMascot pose={pose} size="large" /> : icon}
      <strong>{title}</strong>
      <p>{description}</p>
      {action}
    </div>
  )
}

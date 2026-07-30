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
}: {
  title: string
  description: string
}) {
  return (
    <div className="page-state">
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  )
}

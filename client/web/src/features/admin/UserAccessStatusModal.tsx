import type { FormEvent } from 'react'
import { Modal } from '../../shared/ui/Modal'
import type { AdminUser } from './models'

export function UserAccessStatusModal({
  selected,
  reason,
  reasonError,
  actionError,
  busyUserId,
  onReasonChange,
  onClose,
  onSubmit,
}: {
  selected: AdminUser
  reason: string
  reasonError: string
  actionError: string
  busyUserId: number | null
  onReasonChange: (value: string) => void
  onClose: () => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}) {
  const busy = busyUserId === selected.userId
  return (
    <Modal
      title={`${selected.active ? 'Suspend' : 'Reactivate'} account`}
      description={selected.email}
      onClose={onClose}
    >
      <dl className="detail-grid">
        <div><dt>Role</dt><dd>{selected.role}</dd></div>
        <div>
          <dt>Current status</dt>
          <dd>{selected.active ? 'Active' : 'Suspended'}</dd>
        </div>
      </dl>
      <form className="access-actions" onSubmit={onSubmit}>
        <div className="field-group">
          <label htmlFor="status-reason">Reason</label>
          <textarea
            id="status-reason"
            rows={4}
            value={reason}
            aria-describedby="status-reason-count"
            aria-invalid={reasonError ? 'true' : undefined}
            onChange={(event) => onReasonChange(event.target.value)}
          />
          <small id="status-reason-count">{reason.length}/500 characters</small>
        </div>
        {reasonError && (
          <p className="form-message form-message--error" role="alert">
            {reasonError}
          </p>
        )}
        {actionError && (
          <p className="form-message form-message--error" role="alert">
            {actionError}
          </p>
        )}
        <div className="button-row">
          <button
            className={selected.active ? 'button button--danger' : 'button button--primary'}
            type="submit"
            disabled={busy}
          >
            {busy
              ? 'Saving…'
              : selected.active
                ? 'Suspend account'
                : 'Reactivate account'}
          </button>
          <button
            className="button button--secondary"
            type="button"
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  )
}

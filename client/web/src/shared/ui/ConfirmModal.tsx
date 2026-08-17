import { Modal } from './Modal'

const CONFIRM_BUTTON_CLASS = {
  primary: 'button--primary',
  warning: 'button--warning',
  danger: 'button--danger',
} as const

/**
 * In-app confirm dialog using the shared Modal (not window.confirm).
 */
export function ConfirmModal({
  title,
  description,
  confirmLabel,
  confirming = false,
  tone = 'primary',
  onCancel,
  onConfirm,
}: {
  title: string
  description: string
  confirmLabel: string
  confirming?: boolean
  tone?: 'primary' | 'warning' | 'danger'
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <Modal
      title={title}
      description={description}
      onClose={() => {
        if (!confirming) {
          onCancel()
        }
      }}
    >
      <div className="modal__actions modal__actions--plain">
        <button
          className="button button--secondary"
          type="button"
          disabled={confirming}
          onClick={onCancel}
        >
          Cancel
        </button>
        <button
          className={`button ${CONFIRM_BUTTON_CLASS[tone]}`}
          type="button"
          disabled={confirming}
          onClick={onConfirm}
        >
          {confirming ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

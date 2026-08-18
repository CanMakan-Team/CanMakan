import { Modal } from '../../../shared/ui/Modal'
import type { AdminScanFeedbackItem } from '../api/models'

export function AdminScanFeedbackCommentModal({
  item,
  onClose,
}: {
  item: AdminScanFeedbackItem
  onClose: () => void
}) {
  if (!item.userComments) return null
  return (
    <Modal
      title="User Feedback"
      description={`${item.userEmail ?? 'Unknown user'} · ${item.productName}`}
      onClose={onClose}
    >
      <p>{item.userComments}</p>
    </Modal>
  )
}

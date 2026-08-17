import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmModal } from '../../../shared/ui/ConfirmModal'

describe('ConfirmModal', () => {
  it('calls onConfirm and onCancel from the action buttons', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    const onConfirm = vi.fn()

    render(
      <ConfirmModal
        title="Deactivate Michael Tan?"
        description="They will no longer be selectable for scans."
        confirmLabel="Deactivate"
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByRole('dialog')).toHaveAccessibleName('Deactivate Michael Tan?')
    await user.click(screen.getByRole('button', { name: 'Deactivate' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})

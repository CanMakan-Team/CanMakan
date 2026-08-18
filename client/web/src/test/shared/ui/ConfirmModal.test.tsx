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

  it('applies warning and danger tones', () => {
    const { rerender } = render(
      <ConfirmModal
        title="Warn?"
        description="Warning body"
        confirmLabel="Continue"
        tone="warning"
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    )
    expect(screen.getByRole('button', { name: 'Continue' })).toHaveClass('button--warning')

    rerender(
      <ConfirmModal
        title="Remove?"
        description="Danger body"
        confirmLabel="Remove"
        tone="danger"
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    )
    expect(screen.getByRole('button', { name: 'Remove' })).toHaveClass('button--danger')
  })

  it('disables actions and blocks dismiss while confirming', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    const onConfirm = vi.fn()

    render(
      <ConfirmModal
        title="Working?"
        description="Please wait"
        confirmLabel="Confirm"
        confirming
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByRole('button', { name: 'Working…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Close dialog' }))
    expect(onCancel).not.toHaveBeenCalled()
  })
})

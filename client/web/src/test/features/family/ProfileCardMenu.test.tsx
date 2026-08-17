import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProfileCardMenu } from '../../../features/family/components/ProfileCardMenu'

describe('ProfileCardMenu', () => {
  const onEdit = vi.fn()
  const onToggleActive = vi.fn()
  const onRemove = vi.fn()

  beforeEach(() => {
    onEdit.mockReset()
    onToggleActive.mockReset()
    onRemove.mockReset()
  })

  it('opens lifecycle actions for an active profile and runs them', async () => {
    const user = userEvent.setup()
    render(
      <ProfileCardMenu
        profileActive
        profileName="Jamie"
        onEdit={onEdit}
        onToggleActive={onToggleActive}
        onRemove={onRemove}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Actions for Jamie' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()

    await user.click(screen.getByRole('menuitem', { name: 'Edit dietary profile' }))
    expect(onEdit).toHaveBeenCalledOnce()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Actions for Jamie' }))
    await user.click(screen.getByRole('menuitem', { name: 'Deactivate' }))
    expect(onToggleActive).toHaveBeenCalledOnce()

    await user.click(screen.getByRole('button', { name: 'Actions for Jamie' }))
    await user.click(screen.getByRole('menuitem', { name: 'Remove' }))
    expect(onRemove).toHaveBeenCalledOnce()
  })

  it('shows Reactivate when the profile is inactive', async () => {
    const user = userEvent.setup()
    render(
      <ProfileCardMenu
        profileActive={false}
        profileName="Sam"
        onEdit={onEdit}
        onToggleActive={onToggleActive}
        onRemove={onRemove}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Actions for Sam' }))
    expect(screen.getByRole('menuitem', { name: 'Reactivate' })).toBeInTheDocument()
  })

  it('hides lifecycle actions for the admin self row', async () => {
    const user = userEvent.setup()
    render(
      <ProfileCardMenu
        profileActive
        profileName="Admin"
        allowLifecycleActions={false}
        onEdit={onEdit}
        onToggleActive={onToggleActive}
        onRemove={onRemove}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Actions for Admin' }))
    expect(screen.getByRole('menuitem', { name: 'Edit dietary profile' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Deactivate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Remove' })).not.toBeInTheDocument()
  })

  it('closes on Escape and outside click', async () => {
    const user = userEvent.setup()
    render(
      <div>
        <button type="button">Outside</button>
        <ProfileCardMenu
          profileActive
          profileName="Pat"
          onEdit={onEdit}
          onToggleActive={onToggleActive}
          onRemove={onRemove}
        />
      </div>,
    )

    await user.click(screen.getByRole('button', { name: 'Actions for Pat' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Actions for Pat' }))
    await user.click(screen.getByRole('button', { name: 'Outside' }))
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})

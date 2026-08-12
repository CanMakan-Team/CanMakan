import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { UserAccessPage } from '../../../features/admin/UserAccessPage'
import { adminService } from '../../../features/admin/adminService'
import type { AdminUser } from '../../../features/admin/models'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import { ApiError } from '../../../shared/api/apiErrors'
import { systemAdminSession } from '../../testUtils'

const auditReadSpy = vi.hoisted(() => vi.fn())

vi.mock('../../../features/admin/adminService', () => ({
  adminService: {
    getConsumerTrends: vi.fn(),
    getUsers: vi.fn(),
    updateAccountStatus: vi.fn(),
    getAuditEntries: auditReadSpy,
  },
}))

const activeUser: AdminUser = {
  userId: 21,
  email: 'user21@example.test',
  role: 'USER',
  active: true,
  updatedAt: '2026-08-10T09:30:00',
}

const suspendedAdmin: AdminUser = {
  userId: 22,
  email: 'admin22@example.test',
  role: 'ADMIN',
  active: false,
  updatedAt: '2026-08-10T09:35:00',
}

function renderPage(currentUserId = 1) {
  const baseSession = systemAdminSession()
  const value: SessionContextValue = {
    session: {
      ...baseSession,
      userId: currentUserId,
      roles: [...baseSession.roles],
    },
    loading: false,
    restoring: false,
    restorationError: '',
    retryRestoration: () => undefined,
    loginWithCredentials: async () => {
      throw new Error('unused')
    },
    register: async () => {
      throw new Error('unused')
    },
    logout: async () => undefined,
  }

  return render(
    <SessionContext.Provider value={value}>
      <UserAccessPage />
    </SessionContext.Provider>,
  )
}

async function openStatusModal(account: AdminUser) {
  vi.mocked(adminService.getUsers).mockResolvedValue([account])
  const user = userEvent.setup()
  renderPage()
  await screen.findByText(account.email)
  await user.click(
    screen.getByRole('button', {
      name: account.active ? 'Suspend' : 'Reactivate',
    }),
  )
  return { user, dialog: screen.getByRole('dialog') }
}

describe('UserAccessPage UC13 account status management', () => {
  beforeEach(() => {
    vi.mocked(adminService.getUsers).mockReset()
    vi.mocked(adminService.updateAccountStatus).mockReset()
    auditReadSpy.mockReset()
  })

  it('shows the loading state', () => {
    vi.mocked(adminService.getUsers).mockImplementation(() => new Promise(() => {}))

    renderPage()

    expect(screen.getByText('Loading user accounts…')).toBeInTheDocument()
  })

  it('renders accounts returned by the API', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser])

    renderPage()

    expect(await screen.findByText(activeUser.email)).toBeInTheDocument()
  })

  it('renders the empty state', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('No accounts match')).toBeInTheDocument()
  })

  it('surfaces an account-list API error', async () => {
    vi.mocked(adminService.getUsers).mockRejectedValue(
      new ApiError('Account listing is unavailable.', 500),
    )

    renderPage()

    expect(await screen.findByText('Account listing is unavailable.')).toBeInTheDocument()
  })

  it('displays USER and ADMIN as read-only account data', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser, suspendedAdmin])

    renderPage()

    await screen.findByText(activeUser.email)
    expect(screen.getByRole('cell', { name: 'USER' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'ADMIN' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Set primary role')).not.toBeInTheDocument()
  })

  it('derives Active and Suspended labels from the boolean state', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser, suspendedAdmin])

    renderPage()

    const table = await screen.findByRole('table')
    expect(within(table).getByText('Active')).toBeInTheDocument()
    expect(within(table).getByText('Suspended')).toBeInTheDocument()
  })

  it('does not render obsolete role, pending or disabled controls', async () => {
    const { dialog } = await openStatusModal(activeUser)

    expect(within(dialog).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(dialog).queryByText(/family admin/i)).not.toBeInTheDocument()
    expect(screen.queryByText('PENDING')).not.toBeInTheDocument()
    expect(screen.queryByText('DISABLED')).not.toBeInTheDocument()
  })

  it('opens the Suspend status modal for an active account', async () => {
    const { dialog } = await openStatusModal(activeUser)

    expect(within(dialog).getByRole('heading', { name: 'Suspend account' }))
      .toBeInTheDocument()
    expect(within(dialog).getByText(activeUser.email)).toBeInTheDocument()
  })

  it('opens the Reactivate status modal for a suspended account', async () => {
    const { dialog } = await openStatusModal(suspendedAdmin)

    expect(within(dialog).getByRole('heading', { name: 'Reactivate account' }))
      .toBeInTheDocument()
    expect(within(dialog).getByText(suspendedAdmin.email)).toBeInTheDocument()
  })

  it('prevents submission when the reason is blank', async () => {
    const { user, dialog } = await openStatusModal(activeUser)

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    expect(await within(dialog).findByText(
      'Reason is required for an account status change.',
    )).toBeInTheDocument()
    expect(adminService.updateAccountStatus).not.toHaveBeenCalled()
  })

  it('prevents submission when the trimmed reason exceeds 500 characters', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    fireEvent.change(within(dialog).getByLabelText('Reason'), {
      target: { value: ` ${'a'.repeat(501)} ` },
    })

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    expect(await within(dialog).findByText(
      'Reason must be 500 characters or fewer.',
    )).toBeInTheDocument()
    expect(adminService.updateAccountStatus).not.toHaveBeenCalled()
  })

  it('submits a suspension with active=false and a trimmed reason', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...activeUser,
      active: false,
      changed: true,
    })
    await user.type(within(dialog).getByLabelText('Reason'), '  Policy breach  ')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    await waitFor(() => {
      expect(adminService.updateAccountStatus).toHaveBeenCalledWith(21, {
        active: false,
        reason: 'Policy breach',
      })
    })
  })

  it('submits a reactivation with active=true and a trimmed reason', async () => {
    const { user, dialog } = await openStatusModal(suspendedAdmin)
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...suspendedAdmin,
      active: true,
      changed: true,
    })
    await user.type(within(dialog).getByLabelText('Reason'), '  Review complete  ')

    await user.click(within(dialog).getByRole('button', { name: 'Reactivate account' }))

    await waitFor(() => {
      expect(adminService.updateAccountStatus).toHaveBeenCalledWith(22, {
        active: true,
        reason: 'Review complete',
      })
    })
  })

  it('shows transition feedback when changed=true', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...activeUser,
      active: false,
      changed: true,
    })
    await user.type(within(dialog).getByLabelText('Reason'), 'Policy breach')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    expect(await screen.findByText('Account suspended successfully.'))
      .toBeInTheDocument()
  })

  it('uses neutral feedback when changed=false', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...activeUser,
      changed: false,
    })
    await user.type(within(dialog).getByLabelText('Reason'), 'Policy review')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    expect(await screen.findByText(
      'Account status was already up to date. No changes were required.',
    )).toBeInTheDocument()
    expect(screen.queryByText('Account suspended successfully.'))
      .not.toBeInTheDocument()
  })

  it('refetches the account list after a successful PATCH', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...activeUser,
      active: false,
      changed: true,
    })
    await user.type(within(dialog).getByLabelText('Reason'), 'Policy breach')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    await waitFor(() => expect(adminService.getUsers).toHaveBeenCalledTimes(2))
  })

  it('preserves the applied filters when refetching after PATCH', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser])
    vi.mocked(adminService.updateAccountStatus).mockResolvedValue({
      ...activeUser,
      active: false,
      changed: true,
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(activeUser.email)
    await user.type(screen.getByLabelText('Email search'), '  user21  ')
    await user.selectOptions(screen.getByLabelText('Role'), 'USER')
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: 'Apply filters' }))
    const appliedFilters = { query: 'user21', role: 'USER' as const, active: true }
    await waitFor(() => {
      expect(adminService.getUsers).toHaveBeenCalledWith(appliedFilters)
    })
    await screen.findByText(activeUser.email)
    await user.click(screen.getByRole('button', { name: 'Suspend' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Reason'), 'Policy breach')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    await waitFor(() => {
      const matchingCalls = vi.mocked(adminService.getUsers).mock.calls.filter(
        ([filters]) => JSON.stringify(filters) === JSON.stringify(appliedFilters),
      )
      expect(matchingCalls).toHaveLength(2)
    })
  })

  it('surfaces a backend 409 message without recreating the rule', async () => {
    const { user, dialog } = await openStatusModal(activeUser)
    vi.mocked(adminService.updateAccountStatus).mockRejectedValue(
      new ApiError('The last active administrator cannot be suspended.', 409),
    )
    await user.type(within(dialog).getByLabelText('Reason'), 'Policy breach')

    await user.click(within(dialog).getByRole('button', { name: 'Suspend account' }))

    expect(await screen.findByText(
      'The last active administrator cannot be suspended.',
    )).toBeInTheDocument()
  })

  it('uses backend-aligned email, role and status filters', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(activeUser.email)
    await user.type(screen.getByLabelText('Email search'), '  alice@example.test  ')
    await user.selectOptions(screen.getByLabelText('Role'), 'ADMIN')
    await user.selectOptions(screen.getByLabelText('Status'), 'SUSPENDED')

    await user.click(screen.getByRole('button', { name: 'Apply filters' }))

    await waitFor(() => {
      expect(adminService.getUsers).toHaveBeenLastCalledWith({
        query: 'alice@example.test',
        role: 'ADMIN',
        active: false,
      })
    })
  })

  it('does not request admin audit history', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([activeUser])

    renderPage()
    await screen.findByText(activeUser.email)

    expect(auditReadSpy).not.toHaveBeenCalled()
  })

  it('disables status action for the current session administrator', async () => {
    vi.mocked(adminService.getUsers).mockResolvedValue([
      { ...activeUser, userId: 1 },
    ])

    renderPage(1)

    expect(await screen.findByText('Current admin')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Suspend' })).toBeDisabled()
  })
})

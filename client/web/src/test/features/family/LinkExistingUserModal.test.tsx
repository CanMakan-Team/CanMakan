import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LinkExistingUserModal } from '../../../features/family/components/LinkExistingUserModal'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'
import type { InvitationResponse } from '../../../shared/api/types'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    createInvitation: vi.fn(),
    searchExistingUser: vi.fn(),
  },
}))

function invitation(overrides: Partial<InvitationResponse> = {}): InvitationResponse {
  return {
    invitationId: 1,
    invitedEmail: 'jamie@example.com',
    relationship: 'SPOUSE',
    invitationToken: 'tok',
    inviteCode: 'ABCD1234',
    inviteUrl: 'http://localhost/invite/tok',
    status: 'PENDING',
    expiresAt: '2026-09-01T00:00:00Z',
    inviteeRegistered: false,
    emailSent: true,
    ...overrides,
  }
}

describe('LinkExistingUserModal', () => {
  const onClose = vi.fn()
  const onSuccess = vi.fn()

  beforeEach(() => {
    onClose.mockReset()
    onSuccess.mockReset()
    vi.mocked(familyApiService.createInvitation).mockReset()
    vi.mocked(familyApiService.searchExistingUser).mockReset()
  })

  it('invites on submit without searching first', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.createInvitation).mockResolvedValue(invitation())

    render(<LinkExistingUserModal onClose={onClose} onSuccess={onSuccess} />)

    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Email address'), 'jamie@example.com')
    await user.selectOptions(screen.getByLabelText('Relationship to you'), 'SPOUSE')
    await user.click(screen.getByRole('button', { name: 'Invite' }))

    await waitFor(() => {
      expect(familyApiService.createInvitation).toHaveBeenCalledWith(
        'jamie@example.com',
        'SPOUSE',
      )
    })
    expect(familyApiService.searchExistingUser).not.toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith(
      'Invitation sent to jamie@example.com. Ask them to check their email inbox (and spam folder).',
    )
    expect(onClose).toHaveBeenCalled()
  })

  it('shows backend conflict errors from invite', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.createInvitation).mockRejectedValue(
      new ApiError('That user already belongs to a family circle.', 409),
    )

    render(<LinkExistingUserModal onClose={onClose} onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText('Email address'), 'taken@example.com')
    await user.selectOptions(screen.getByLabelText('Relationship to you'), 'CHILD')
    await user.click(screen.getByRole('button', { name: 'Invite' }))

    expect(
      await screen.findByText('That user already belongs to a family circle.'),
    ).toBeInTheDocument()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('treats emailSent false as a failure', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.createInvitation).mockResolvedValue(
      invitation({ emailSent: false }),
    )

    render(<LinkExistingUserModal onClose={onClose} onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText('Email address'), 'jamie@example.com')
    await user.selectOptions(screen.getByLabelText('Relationship to you'), 'OTHER')
    await user.click(screen.getByRole('button', { name: 'Invite' }))

    expect(
      await screen.findByText(
        'The invitation email could not be sent. Try again in a moment.',
      ),
    ).toBeInTheDocument()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('requires email and relationship before calling the API', async () => {
    const user = userEvent.setup()
    render(<LinkExistingUserModal onClose={onClose} onSuccess={onSuccess} />)

    await user.click(screen.getByRole('button', { name: 'Invite' }))
    expect(await screen.findByText('Enter an email address.')).toBeInTheDocument()
    expect(familyApiService.createInvitation).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Email address'), 'jamie@example.com')
    await user.click(screen.getByRole('button', { name: 'Invite' }))
    expect(await screen.findByText('Select a relationship.')).toBeInTheDocument()
    expect(familyApiService.createInvitation).not.toHaveBeenCalled()
  })
})

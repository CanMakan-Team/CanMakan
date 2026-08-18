import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FamilyMembersPage } from '../../../features/family/pages/FamilyMembersPage'
import { familyApiService } from '../../../features/family/api/familyApiService'
import type { FamilyMember } from '../../../shared/api/types'
import { ApiError } from '../../../shared/api/apiErrors'

vi.mock('../../../features/family/useFamilyMe', () => ({
  useFamilyMe: () => ({
    family: {
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 1,
      createdByUserId: 14,
    },
    isPrimaryAdmin: true,
    reload: vi.fn(),
    loading: false,
    error: '',
    hasFamily: true,
  }),
}))

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMembers: vi.fn(),
    setProfileActive: vi.fn(),
    removeDependantProfile: vi.fn(),
    removeMember: vi.fn(),
  },
}))

vi.mock('../../../features/family/components/LinkExistingUserModal', () => ({
  LinkExistingUserModal: ({
    onClose,
    onSuccess,
  }: {
    onClose: () => void
    onSuccess: (message: string) => void
  }) => (
    <div role="dialog" aria-label="Invite to Family">
      <button type="button" onClick={() => onSuccess('Invitation sent to jamie@example.com.')}>
        Finish invite
      </button>
      <button type="button" onClick={onClose}>
        Close invite
      </button>
    </div>
  ),
}))

vi.mock('../../../features/family/components/CreateFamilyProfileModal', () => ({
  CreateFamilyProfileModal: ({ onClose }: { onClose: () => void }) => (
    <div role="dialog" aria-label="Create profile">
      <button type="button" onClick={onClose}>
        Close create
      </button>
    </div>
  ),
}))

vi.mock('../../../features/family/components/EditFamilyProfileModal', () => ({
  EditFamilyProfileModal: ({
    member,
    onClose,
  }: {
    member: FamilyMember
    onClose: () => void
  }) => (
    <div role="dialog" aria-label={`Edit ${member.profileName}`}>
      <button type="button" onClick={onClose}>
        Close edit
      </button>
    </div>
  ),
}))

function member(partial: Partial<FamilyMember> & Pick<FamilyMember, 'profileId' | 'profileName'>): FamilyMember {
  return {
    memberId: partial.profileId,
    linkedUserId: partial.linkedUserId ?? null,
    relationship: partial.relationship ?? 'CHILD',
    commonRequirements: partial.commonRequirements ?? [],
    restrictions: partial.restrictions ?? [],
    source: partial.source ?? 'DEPENDANT_PROFILE',
    profileActive: partial.profileActive ?? true,
    maskedEmail: partial.maskedEmail,
    memberRole: partial.memberRole,
    ...partial,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/family/members']}>
      <Routes>
        <Route path="/family/members" element={<FamilyMembersPage />} />
        <Route path="/me" element={<p>Personal home</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('FamilyMembersPage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMembers).mockReset()
    vi.mocked(familyApiService.setProfileActive).mockReset()
    vi.mocked(familyApiService.removeDependantProfile).mockReset()
    vi.mocked(familyApiService.removeMember).mockReset()
    vi.mocked(familyApiService.setProfileActive).mockResolvedValue({
      id: 2,
      profileName: 'Child Profile',
      active: false,
    })
    vi.mocked(familyApiService.removeDependantProfile).mockResolvedValue()
    vi.mocked(familyApiService.removeMember).mockResolvedValue()
  })

  it('renders members and opens invite, create, and edit flows', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMembers).mockResolvedValue([
      member({
        profileId: 1,
        profileName: 'Admin Self',
        relationship: 'SELF',
        source: 'REGISTERED_USER',
        linkedUserId: 14,
        maskedEmail: 'a***@example.com',
        commonRequirements: ['HALAL'],
      }),
      member({
        profileId: 2,
        profileName: 'Child Profile',
        source: 'DEPENDANT_PROFILE',
        restrictions: ['PEANUT'],
      }),
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Family Members' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Admin Self' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Child Profile' })).toBeInTheDocument()
    expect(screen.getByText('App User')).toBeInTheDocument()
    expect(screen.getByText('Family profile')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Invite to Family' }))
    expect(screen.getByRole('dialog', { name: 'Invite to Family' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Finish invite' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Invitation sent')

    await user.click(screen.getByRole('button', { name: 'Create New Profile' }))
    expect(screen.getByRole('dialog', { name: 'Create profile' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close create' }))

    await user.click(screen.getByRole('button', { name: 'Actions for Child Profile' }))
    await user.click(screen.getByRole('menuitem', { name: 'Edit dietary profile' }))
    expect(screen.getByRole('dialog', { name: 'Edit Child Profile' })).toBeInTheDocument()
  })

  it('confirms deactivate then shows success notice', async () => {
    const user = userEvent.setup()
    const child = member({ profileId: 2, profileName: 'Child Profile', profileActive: true })
    vi.mocked(familyApiService.getMembers)
      .mockResolvedValueOnce([child])
      .mockResolvedValueOnce([{ ...child, profileActive: false }])

    renderPage()
    await screen.findByRole('heading', { name: 'Child Profile' })

    await user.click(screen.getByRole('button', { name: 'Actions for Child Profile' }))
    await user.click(screen.getByRole('menuitem', { name: 'Deactivate' }))
    expect(screen.getByRole('dialog', { name: 'Deactivate Child Profile?' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Deactivate' }))
    await waitFor(() => {
      expect(familyApiService.setProfileActive).toHaveBeenCalledWith(2, false)
    })
    expect(await screen.findByRole('status')).toHaveTextContent('was deactivated')
  })

  it('reactivates an inactive profile without a confirm dialog', async () => {
    const user = userEvent.setup()
    const child = member({ profileId: 2, profileName: 'Child Profile', profileActive: false })
    vi.mocked(familyApiService.getMembers)
      .mockResolvedValueOnce([child])
      .mockResolvedValueOnce([{ ...child, profileActive: true }])

    renderPage()
    await screen.findByRole('heading', { name: 'Child Profile' })
    expect(screen.getByText('Inactive', { selector: '.source-label' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Actions for Child Profile' }))
    await user.click(screen.getByRole('menuitem', { name: 'Reactivate' }))
    await waitFor(() => {
      expect(familyApiService.setProfileActive).toHaveBeenCalledWith(2, true)
    })
    expect(await screen.findByRole('status')).toHaveTextContent('is active again')
  })

  it('removes a dependant after confirm', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMembers)
      .mockResolvedValueOnce([
        member({ profileId: 2, profileName: 'Child Profile', source: 'DEPENDANT_PROFILE' }),
      ])
      .mockResolvedValueOnce([])

    renderPage()
    await screen.findByRole('heading', { name: 'Child Profile' })

    await user.click(screen.getByRole('button', { name: 'Actions for Child Profile' }))
    await user.click(screen.getByRole('menuitem', { name: 'Remove' }))
    expect(screen.getByRole('dialog', { name: 'Remove Child Profile?' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() => {
      expect(familyApiService.removeDependantProfile).toHaveBeenCalledWith(2)
    })
    expect(await screen.findByRole('status')).toHaveTextContent('was removed')
  })

  it('removes a linked app user after confirm', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMembers)
      .mockResolvedValueOnce([
        member({
          profileId: 3,
          profileName: 'Spouse',
          source: 'REGISTERED_USER',
          linkedUserId: 99,
        }),
      ])
      .mockResolvedValueOnce([])

    renderPage()
    await screen.findByRole('heading', { name: 'Spouse' })

    await user.click(screen.getByRole('button', { name: 'Actions for Spouse' }))
    await user.click(screen.getByRole('menuitem', { name: 'Remove' }))
    expect(
      screen.getByRole('dialog', { name: 'Remove Spouse from the family circle?' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() => {
      expect(familyApiService.removeMember).toHaveBeenCalledWith(99)
    })
  })

  it('shows empty state when there are no members', async () => {
    vi.mocked(familyApiService.getMembers).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('No family profiles yet')).toBeInTheDocument()
  })

  it('shows an error state when members fail to load', async () => {
    vi.mocked(familyApiService.getMembers).mockRejectedValue(new ApiError('Members unavailable.'))
    renderPage()
    expect(await screen.findByText('Members unavailable.')).toBeInTheDocument()
  })
})

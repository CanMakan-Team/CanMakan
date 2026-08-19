import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ME_PATH } from '../../../app/userPortalPaths'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyMember } from '../../../shared/api/types'
import { ConfirmModal } from '../../../shared/ui/ConfirmModal'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { CreateFamilyProfileModal } from '../components/CreateFamilyProfileModal'
import { EditFamilyProfileModal } from '../components/EditFamilyProfileModal'
import { LinkExistingUserModal } from '../components/LinkExistingUserModal'
import { ProfileCardMenu } from '../components/ProfileCardMenu'
import { ScanEligibilityCard } from '../components/ScanEligibilityCard'
import { formatCode } from '../lib/profileOptions'
import { isCurrentAdminProfile } from '../lib/familyRoles'
import { profileDisplayCaption } from '../lib/profileDisplay'
import { useFamilyMe } from '../useFamilyMe'

/**
 * FamilyMembersPage component for displaying the family members
 *
 * @author Amelia
 * @author YangMaowei
 */

type OpenModal = 'link' | 'create' | null

type PendingConfirm =
  | { type: 'deactivate'; member: FamilyMember }
  | { type: 'remove'; member: FamilyMember }

function profileSourceLabel(member: Pick<FamilyMember, 'profileActive' | 'source'>): string {
  if (!member.profileActive) return 'Inactive'
  if (member.source === 'REGISTERED_USER') return 'App User'
  return 'Family profile'
}

function confirmCopy(pending: PendingConfirm) {
  if (pending.type === 'deactivate') {
    return {
      title: `Deactivate ${pending.member.profileName}?`,
      description: 'They will no longer be selectable for scans.',
      confirmLabel: 'Deactivate',
      tone: 'warning' as const,
    }
  }
  if (pending.member.source === 'DEPENDANT_PROFILE') {
    return {
      title: `Remove ${pending.member.profileName}?`,
      description: 'Scan history is kept.',
      confirmLabel: 'Remove',
      tone: 'danger' as const,
    }
  }
  return {
    title: `Remove ${pending.member.profileName} from the family circle?`,
    description: 'This person will leave the household roster.',
    confirmLabel: 'Remove',
    tone: 'danger' as const,
  }
}

export function FamilyMembersPage() {
  const { family, isPrimaryAdmin, reload, loading: familyLoading } = useFamilyMe()
  const selfProfileId = family?.selfProfileId ?? null
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [openModal, setOpenModal] = useState<OpenModal>(null)
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirm | null>(null)

  /** Load the family members from the API. */
  const loadMembers = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const loadedMembers = await familyApiService.getMembers()
      setMembers(loadedMembers)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (familyLoading || !isPrimaryAdmin) {
      return
    }
    const timeoutId = window.setTimeout(() => void loadMembers(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadMembers, familyLoading, isPrimaryAdmin])

  // Success copy (deactivate, invite, remove) is announced then cleared so it does not linger.
  useEffect(() => {
    if (!notice) {
      return
    }
    const timeoutId = window.setTimeout(() => setNotice(''), 5000)
    return () => window.clearTimeout(timeoutId)
  }, [notice])

  if (familyLoading) {
    return <LoadingState label="Loading family members…" />
  }
  if (!isPrimaryAdmin) {
    return <Navigate to={ME_PATH} replace />
  }

  const handleSuccess = (message: string) => {
    setNotice(message)
    reload()
    void loadMembers()
  }

  /** Toggle the active status of a family member. */
  const toggleActive = async (member: FamilyMember) => {
    if (isCurrentAdminProfile(member, selfProfileId)) {
      return
    }
    const nextActive = !member.profileActive
    setBusyId(member.profileId)
    setError('')
    try {
      await familyApiService.setProfileActive(member.profileId, nextActive)
      setPendingConfirm(null)
      setNotice(
        nextActive
          ? `${member.profileName} is active again.`
          : `${member.profileName} was deactivated.`,
      )
      await loadMembers()
    } catch (caughtError) {
      setPendingConfirm(null)
      setError(getErrorMessage(caughtError))
    } finally {
      setBusyId(null)
    }
  }

  const requestToggleActive = (member: FamilyMember) => {
    if (isCurrentAdminProfile(member, selfProfileId)) {
      return
    }
    if (member.profileActive) {
      setPendingConfirm({ type: 'deactivate', member })
      return
    }
    void toggleActive(member)
  }

  /** Remove a family member from the family circle. */
  const removeMember = async (member: FamilyMember) => {
    if (isCurrentAdminProfile(member, selfProfileId)) {
      return
    }
    setBusyId(member.profileId)
    setError('')
    try {
      if (member.source === 'DEPENDANT_PROFILE') {
        await familyApiService.removeDependantProfile(member.profileId)
      } else if (member.linkedUserId != null) {
        await familyApiService.removeMember(member.linkedUserId)
      } else {
        throw new Error('This member cannot be removed.')
      }
      setPendingConfirm(null)
      setNotice(`${member.profileName} was removed from the family roster.`)
      reload()
      await loadMembers()
    } catch (caughtError) {
      setPendingConfirm(null)
      setError(getErrorMessage(caughtError))
    } finally {
      setBusyId(null)
    }
  }

  const requestRemoveMember = (member: FamilyMember) => {
    if (isCurrentAdminProfile(member, selfProfileId)) {
      return
    }
    setPendingConfirm({ type: 'remove', member })
  }

  /** Render the family members page. PRIMARY_ADMIN only (FamilyMeGate + redirect). */
  return (
    <>
      <header className="page-header page-header--split">
        <p className="eyebrow">Family Circle</p>
        <div className="page-header__title-row">
          <h1>Family Members</h1>
          <div className="page-header__actions">
            <button
              className="button button--secondary"
              type="button"
              onClick={() => setOpenModal('link')}
            >
              Invite to Family
            </button>
            <button
              className="button button--primary"
              type="button"
              onClick={() => setOpenModal('create')}
            >
              Create New Profile
            </button>
          </div>
        </div>
        <p>
          Link a registered App User or create a non-login dependant profile.
          Edit metadata, amend scan eligibility, or soft-remove members.
        </p>
      </header>

      {notice ? (
        <output className="success-inline">
          {notice}
        </output>
      ) : null}

      {loading && <LoadingState label="Loading family members…" />}
      {!loading && members.length === 0 && error && (
        <ErrorState message={error} onRetry={loadMembers} />
      )}
      {!loading && members.length === 0 && !error && (
        <EmptyState
          title="No family profiles yet"
          description="Link an existing App User or create a new dependant profile."
        />
      )}
      {!loading && members.length > 0 && (
        <>
          {error ? (
            <p className="form-message form-message--error" role="alert">
              {error}
            </p>
          ) : null}
          <ScanEligibilityCard members={members} />
          <section className="profile-grid" aria-label="Family member profiles">
            {members.map((member) => {
              const codes = [...member.commonRequirements, ...member.restrictions]
              const busy = busyId === member.profileId
              const caption = profileDisplayCaption(member, {
                selfProfileId,
                isPrimaryAdmin: true,
              })
              const isAdminRow = isCurrentAdminProfile(member, selfProfileId)
              return (
                <article
                  className={`profile-card${!member.profileActive ? ' profile-card--inactive' : ''}`}
                  key={member.profileId}
                >
                  <div className="profile-card__header">
                    <span className="avatar avatar--large" aria-hidden="true">
                      {member.profileName.slice(0, 1)}
                    </span>
                    <div>
                      <h2>{member.profileName}</h2>
                      {caption ? <p>{caption}</p> : null}
                    </div>
                    <div className="profile-card__meta">
                      <span className="source-label">
                        {profileSourceLabel(member)}
                      </span>
                      <ProfileCardMenu
                        disabled={busy}
                        profileActive={member.profileActive}
                        profileName={member.profileName}
                        allowLifecycleActions={!isAdminRow}
                        onEdit={() => setEditingMember(member)}
                        onToggleActive={() => requestToggleActive(member)}
                        onRemove={() => requestRemoveMember(member)}
                      />
                    </div>
                  </div>
                  {member.maskedEmail && (
                    <p className="masked-email">{member.maskedEmail}</p>
                  )}
                  <div className="tag-list" aria-label={`${member.profileName} requirements`}>
                    {codes.length ? (
                      codes.map((code) => <span key={code}>{formatCode(code)}</span>)
                    ) : (
                      <span className="tag--empty">No restrictions recorded</span>
                    )}
                  </div>
                </article>
              )
            })}
          </section>
        </>
      )}

      {openModal === 'link' && (
        <LinkExistingUserModal
          onClose={() => setOpenModal(null)}
          onSuccess={handleSuccess}
        />
      )}
      {openModal === 'create' && (
        <CreateFamilyProfileModal
          onClose={() => setOpenModal(null)}
          onSuccess={handleSuccess}
        />
      )}
      {editingMember && (
        <EditFamilyProfileModal
          member={editingMember}
          isPrimaryAdmin
          onClose={() => setEditingMember(null)}
          onSuccess={handleSuccess}
        />
      )}
      {pendingConfirm ? (
        <ConfirmModal
          {...confirmCopy(pendingConfirm)}
          confirming={busyId === pendingConfirm.member.profileId}
          onCancel={() => setPendingConfirm(null)}
          onConfirm={() => {
            if (pendingConfirm.type === 'deactivate') {
              void toggleActive(pendingConfirm.member)
            } else {
              void removeMember(pendingConfirm.member)
            }
          }}
        />
      ) : null}
    </>
  )
}

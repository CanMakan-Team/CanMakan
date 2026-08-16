import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyMember } from '../../../shared/api/types'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { ActiveProfileSelector } from '../components/ActiveProfileSelector'
import { CreateFamilyProfileModal } from '../components/CreateFamilyProfileModal'
import { EditFamilyProfileModal } from '../components/EditFamilyProfileModal'
import { LinkExistingUserModal } from '../components/LinkExistingUserModal'
import { formatCode } from '../lib/profileOptions'
import { profileDisplayCaption } from '../lib/profileDisplay'
import { useFamilyMe } from '../useFamilyMe'

/**
 * FamilyMembersPage component for displaying the family members
 *
 * @author Amelia
 * @author YangMaowei
 */

type OpenModal = 'link' | 'create' | null

export function FamilyMembersPage() {
  const { family, isPrimaryAdmin } = useFamilyMe()
  const selfProfileId = family?.selfProfileId ?? null
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [openModal, setOpenModal] = useState<OpenModal>(null)
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null)

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
    const timeoutId = window.setTimeout(() => void loadMembers(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadMembers])

  const handleSuccess = (message: string) => {
    setNotice(message)
    void loadMembers()
  }

  /** Toggle the active status of a family member. */
  const toggleActive = async (member: FamilyMember) => {
    const nextActive = !member.profileActive
    if (
      !nextActive &&
      !window.confirm(
        `Deactivate ${member.profileName}? They will no longer be selectable for scans.`,
      )
    ) {
      return
    }
    setBusyId(member.profileId)
    setError('')
    try {
      await familyApiService.setProfileActive(member.profileId, nextActive)
      setNotice(
        nextActive
          ? `${member.profileName} is active again.`
          : `${member.profileName} was deactivated.`,
      )
      await loadMembers()
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setBusyId(null)
    }
  }

  /** Remove a family member from the family circle. */
  const removeMember = async (member: FamilyMember) => {
    const label =
      member.source === 'DEPENDANT_PROFILE'
        ? `Remove dependant profile ${member.profileName}? Scan history is kept.`
        : `Remove ${member.profileName} from the family circle?`
    if (!window.confirm(label)) {
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
      setNotice(`${member.profileName} was removed from the family roster.`)
      await loadMembers()
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setBusyId(null)
    }
  }

  /** Render the family members page. */
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Family profiles</p>
          <h1>Family Members</h1>
          <p>
            {isPrimaryAdmin
              ? 'Link a registered App User or create a non-login dependant profile. Edit metadata, toggle scan eligibility, or soft-remove members.'
              : 'View family profiles and dietary requirements. Only the primary admin can invite, edit, or remove members.'}
          </p>
        </div>
        {isPrimaryAdmin && (
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
        )}
      </header>

      <div className="sr-live" aria-live="polite">
        {notice}
      </div>

      {loading ? (
        <LoadingState label="Loading family members…" />
      ) : error ? (
        <ErrorState message={error} onRetry={loadMembers} />
      ) : members.length === 0 ? (
        <EmptyState
          title="No family profiles yet"
          description={
            isPrimaryAdmin
              ? 'Link an existing App User or create a new dependant profile.'
              : 'Ask your family primary admin to add profiles.'
          }
        />
      ) : (
        <>
          <ActiveProfileSelector members={members} />
          <section className="profile-grid" aria-label="Family member profiles">
            {members.map((member) => {
              const codes = [...member.commonRequirements, ...member.restrictions]
              const busy = busyId === member.profileId
              const caption = profileDisplayCaption(member, {
                selfProfileId,
                isPrimaryAdmin,
              })
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
                      {isPrimaryAdmin ? (
                        <p>{formatCode(member.ageGroup)}</p>
                      ) : null}
                    </div>
                    <span className="source-label">
                      {!member.profileActive
                        ? 'Inactive'
                        : member.source === 'REGISTERED_USER'
                          ? 'App User'
                          : 'Family profile'}
                    </span>
                  </div>
                  {member.maskedEmail && <p className="masked-email">{member.maskedEmail}</p>}
                  <div className="tag-list" aria-label={`${member.profileName} requirements`}>
                    {codes.length ? (
                      codes.map((code) => <span key={code}>{formatCode(code)}</span>)
                    ) : (
                      <span className="tag--empty">No restrictions recorded</span>
                    )}
                  </div>
                  {isPrimaryAdmin && (
                    <div className="profile-card__actions">
                      <button
                        className="button button--secondary button--full"
                        type="button"
                        disabled={busy}
                        onClick={() => setEditingMember(member)}
                      >
                        Edit dietary profile
                      </button>
                      <button
                        className="button button--secondary button--full"
                        type="button"
                        disabled={busy}
                        onClick={() => void toggleActive(member)}
                      >
                        {member.profileActive ? 'Deactivate' : 'Reactivate'}
                      </button>
                      <button
                        className="button button--danger button--full"
                        type="button"
                        disabled={busy}
                        onClick={() => void removeMember(member)}
                      >
                        Remove
                      </button>
                    </div>
                  )}
                </article>
              )
            })}
          </section>
        </>
      )}

      {isPrimaryAdmin && openModal === 'link' && (
        <LinkExistingUserModal
          onClose={() => setOpenModal(null)}
          onSuccess={handleSuccess}
        />
      )}
      {isPrimaryAdmin && openModal === 'create' && (
        <CreateFamilyProfileModal
          onClose={() => setOpenModal(null)}
          onSuccess={handleSuccess}
        />
      )}
      {isPrimaryAdmin && editingMember && (
        <EditFamilyProfileModal
          member={editingMember}
          isPrimaryAdmin={isPrimaryAdmin}
          onClose={() => setEditingMember(null)}
          onSuccess={handleSuccess}
        />
      )}
    </>
  )
}

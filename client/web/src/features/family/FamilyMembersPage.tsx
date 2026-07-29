import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../../api/apiErrors'
import { familyService } from '../../api/familyService'
import type { FamilyMember } from '../../api/types'
import { EmptyState, ErrorState, LoadingState } from '../../components/PageState'
import { ActiveProfileSelector } from './ActiveProfileSelector'
import { CreateFamilyProfileModal } from './CreateFamilyProfileModal'
import { EditFamilyProfileModal } from './EditFamilyProfileModal'
import { LinkExistingUserModal } from './LinkExistingUserModal'
import { formatCode } from './profileOptions'

type OpenModal = 'link' | 'create' | null

export function FamilyMembersPage() {
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [openModal, setOpenModal] = useState<OpenModal>(null)
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null)

  const loadMembers = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setMembers(await familyService.getMembers())
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

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Family profiles</p>
          <h1>Family Members</h1>
          <p>
            Link a registered App User or create a non-login dependant profile
            through two separate flows.
          </p>
        </div>
        <div className="page-header__actions">
          <button
            className="button button--secondary"
            type="button"
            onClick={() => setOpenModal('link')}
          >
            Add Existing App User
          </button>
          <button
            className="button button--primary"
            type="button"
            onClick={() => setOpenModal('create')}
          >
            Create New Profile
          </button>
        </div>
      </header>

      <div className="sr-live" aria-live="polite">{notice}</div>

      {loading ? (
        <LoadingState label="Loading family members…" />
      ) : error ? (
        <ErrorState message={error} onRetry={loadMembers} />
      ) : members.length === 0 ? (
        <EmptyState
          title="No family profiles yet"
          description="Link an existing App User or create a new dependant profile."
        />
      ) : (
        <>
          <ActiveProfileSelector members={members} />
          <section className="profile-grid" aria-label="Family member profiles">
            {members.map((member) => {
              const codes = [...member.commonRequirements, ...member.restrictions]
              return (
                <article className="profile-card" key={member.memberId}>
                  <div className="profile-card__header">
                    <span className="avatar avatar--large" aria-hidden="true">
                      {member.profileName.slice(0, 1)}
                    </span>
                    <div>
                      <h2>{member.profileName}</h2>
                      <p>
                        {formatCode(member.relationship)} · {formatCode(member.ageGroup)}
                      </p>
                    </div>
                    <span className="source-label">
                      {member.source === 'REGISTERED_USER'
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
                  <button
                    className="button button--secondary button--full"
                    type="button"
                    onClick={() => setEditingMember(member)}
                  >
                    Edit dietary profile
                  </button>
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
          onClose={() => setEditingMember(null)}
          onSuccess={handleSuccess}
        />
      )}
    </>
  )
}

import { useState } from 'react'
import { useSession } from '../../auth/useSession'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyMember, FamilyProfileInput } from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { ProfileForm } from './ProfileForm'

/**
 * EditFamilyProfileModal component
 * 
 * @author Amelia
 * @author YangMaowei
 */

/** D3: self linked profile or unlinked dependant only. */
function mayEditRestrictions(member: FamilyMember, userId: number | undefined): boolean {
  if (member.source === 'DEPENDANT_PROFILE') {
    return true
  }
  return member.linkedUserId != null && member.linkedUserId === userId
}

export function EditFamilyProfileModal({
  member,
  onClose,
  onSuccess,
}: {
  member: FamilyMember
  onClose: () => void
  onSuccess: (message: string) => void
}) {
  const { session } = useSession()
  const allowRestrictionEdit = mayEditRestrictions(member, session?.userId)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const initialValue: FamilyProfileInput = {
    profileName: member.profileName,
    relationship: member.relationship,
    ageGroup: member.ageGroup,
    commonRequirements: member.commonRequirements,
    restrictions: member.restrictions,
  }

  const submit = async (input: FamilyProfileInput) => {
    if (saving) return
    setSaving(true)
    setError('')
    try {
      const updated = await familyApiService.updateProfile(member.profileId, input, {
        includeRestrictions: allowRestrictionEdit,
      })
      onSuccess(`${updated.profileName}’s dietary profile was updated.`)
      onClose()
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={`Edit ${member.profileName}`}
      description="Review and update this member’s profile and dietary requirements."
      onClose={onClose}
      wide
    >
      <ProfileForm
        initialValue={initialValue}
        submitLabel="Save changes"
        saving={saving}
        error={error}
        onSubmit={submit}
        onCancel={onClose}
        allowRestrictionEdit={allowRestrictionEdit}
        restrictionEditHint="Restrictions for another adult’s linked profile can only be edited by that member."
      />
    </Modal>
  )
}

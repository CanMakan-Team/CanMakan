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

/** D3: PRIMARY_ADMIN may edit any family member; others only their own profile. */
function mayEditRestrictions(
  member: FamilyMember,
  userId: number | undefined,
  isPrimaryAdmin: boolean,
): boolean {
  if (isPrimaryAdmin) {
    return true
  }
  return member.linkedUserId != null && member.linkedUserId === userId
}

export function EditFamilyProfileModal({
  member,
  isPrimaryAdmin = false,
  onClose,
  onSuccess,
}: {
  member: FamilyMember
  isPrimaryAdmin?: boolean
  onClose: () => void
  onSuccess: (message: string) => void
}) {
  const { session } = useSession()
  const allowRestrictionEdit = mayEditRestrictions(
    member,
    session?.userId,
    isPrimaryAdmin,
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const initialValue: FamilyProfileInput = {
    profileName: member.profileName,
    relationship: member.relationship,
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
        restrictionEditHint="Only the family admin can edit another member's dietary restrictions."
      />
    </Modal>
  )
}

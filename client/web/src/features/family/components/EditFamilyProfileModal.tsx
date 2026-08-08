import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyMember, FamilyProfileInput } from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { ProfileForm } from './ProfileForm'

export function EditFamilyProfileModal({
  member,
  onClose,
  onSuccess,
}: {
  member: FamilyMember
  onClose: () => void
  onSuccess: (message: string) => void
}) {
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
      const updated = await familyApiService.updateProfile(member.memberId, input)
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
      />
    </Modal>
  )
}

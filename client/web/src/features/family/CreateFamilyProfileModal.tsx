import { useState } from 'react'
import { getErrorMessage } from '../../api/apiErrors'
import { familyService } from '../../api/familyService'
import type { FamilyProfileInput } from '../../api/types'
import { Modal } from '../../components/Modal'
import { ProfileForm } from './ProfileForm'

export function CreateFamilyProfileModal({
  onClose,
  onSuccess,
}: {
  onClose: () => void
  onSuccess: (message: string) => void
}) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (input: FamilyProfileInput) => {
    if (saving) return
    setSaving(true)
    setError('')
    try {
      const member = await familyService.createProfile(input)
      onSuccess(`${member.profileName} was created as a non-login family profile.`)
      onClose()
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Create New Family Member Profile"
      description="For a child, dependant or family member who does not need a separate login."
      onClose={onClose}
      wide
    >
      <ProfileForm
        submitLabel="Create profile"
        saving={saving}
        error={error}
        onSubmit={submit}
        onCancel={onClose}
      />
    </Modal>
  )
}

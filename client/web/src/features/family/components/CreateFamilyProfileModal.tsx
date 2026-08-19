import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyProfileInput } from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { ProfileForm } from './ProfileForm'

/** UC8 Create family profile modal
 * 
 * @author Amelia
 */

export function CreateFamilyProfileModal({
  onClose,
  onSuccess,
}: Readonly<{
  onClose: () => void
  onSuccess: (message: string) => void
}>) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  /* Define the submit function */
  const submit = async (input: FamilyProfileInput) => {
    if (saving) return
    setSaving(true)
    setError('')
    try {
      // Create a family profile
      const member = await familyApiService.createProfile(input)
      // Get the profile name
      const profileName =
        'profileName' in member ? member.profileName : 'Family profile'
      onSuccess(`${profileName} was created as a non-login family profile.`)
      onClose()
    } catch (caughtError) {
      // Set the error
      setError(getErrorMessage(caughtError))
    } finally {
      // Set the saving state to false
      setSaving(false)
    }
  }

  /* Define the render function */
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

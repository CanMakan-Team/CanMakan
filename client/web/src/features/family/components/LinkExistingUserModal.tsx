import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { Relationship } from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { getEmailValidationError } from '../../../shared/validation/email'
import { relationshipOptions } from '../lib/profileOptions'

/**
 * Invite a person by email into the family circle (same flow as mobile).
 * Integrity checks (already in a family, duplicate pending invite, email delivery)
 * are handled by POST /invitations — this modal only validates the form and shows
 * the API success or error outcome.
 *
 * @author Amelia
 * @author YangMaowei
 */

export function LinkExistingUserModal({
  onClose,
  onSuccess,
}: Readonly<{
  onClose: () => void
  onSuccess: (message: string) => void
}>) {
  const [email, setEmail] = useState('')
  const [relationship, setRelationship] = useState<Exclude<Relationship, 'SELF'> | ''>('')
  const [inviting, setInviting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const invite = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (inviting) return

    const trimmedEmail = email.trim()
    if (!trimmedEmail) {
      setErrorMessage('Enter an email address.')
      return
    }

    const emailError = getEmailValidationError(trimmedEmail)
    if (emailError) {
      setErrorMessage(emailError)
      return
    }

    if (!relationship) {
      setErrorMessage('Select a relationship.')
      return
    }

    setInviting(true)
    setErrorMessage('')
    try {
      const created = await familyApiService.createInvitation(trimmedEmail, relationship)
      if (!created.emailSent) {
        setErrorMessage(
          'The invitation email could not be sent. Try again in a moment.',
        )
        return
      }
      onSuccess(`Invitation sent to ${created.invitedEmail}. Ask them to check their email inbox (and spam folder).`)
      onClose()
    } catch (caughtError) {
      setErrorMessage(getErrorMessage(caughtError))
    } finally {
      setInviting(false)
    }
  }

  return (
    <Modal
      title="Invite to Family"
      description="Enter their email and relationship. CanMakan checks whether they can join, then sends the invitation."
      onClose={onClose}
    >
      <form className="invite-modal-form" onSubmit={(event) => void invite(event)}>
        <div className="field-group">
          <label htmlFor="invite-email">Email address</label>
          <input
            id="invite-email"
            type="email"
            value={email}
            placeholder="jamie@example.com"
            autoComplete="email"
            disabled={inviting}
            onChange={(event) => {
              setEmail(event.target.value)
              if (errorMessage) setErrorMessage('')
            }}
          />
        </div>

        <div className="field-group">
          <label htmlFor="invite-relationship">Relationship to you</label>
          <select
            id="invite-relationship"
            value={relationship}
            disabled={inviting}
            onChange={(event) => {
              setRelationship(event.target.value as Exclude<Relationship, 'SELF'> | '')
              if (errorMessage) setErrorMessage('')
            }}
          >
            <option value="">Select relationship</option>
            {relationshipOptions
              .filter((option) => option.value !== 'SELF')
              .map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
          </select>
        </div>

        {errorMessage ? (
          <p className="form-message form-message--error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className="invite-modal-form__actions">
          <button
            className="button button--secondary"
            type="button"
            disabled={inviting}
            onClick={onClose}
          >
            Cancel
          </button>
          <button className="button button--primary" type="submit" disabled={inviting}>
            {inviting ? 'Sending invite…' : 'Invite'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type {
  ExistingUserSearchResult,
  InvitationResponse,
  Relationship,
} from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { getEmailValidationError } from '../../../shared/validation/email'
import { relationshipOptions } from '../lib/profileOptions'

/** Link existing user modal component 
 * 
 * @author Amelia
 * @author YangMaowei
*/

/* Define the search state enum */
// initial: initial state
// searching: searching for the user
// found: user found
// not-registered: user not registered
// already-linked: user already linked
// pending: user pending invitation
// inviting: inviting the user
// invited: user invited
// error: error state
type SearchState =
  | 'initial'
  | 'searching'
  | 'found'
  | 'not-registered'
  | 'already-linked'
  | 'pending'
  | 'inviting'
  | 'invited'
  | 'error'

/* Define the LinkExistingUserModal component */
export function LinkExistingUserModal({
  onClose,
  onSuccess,
}: {
  onClose: () => void
  onSuccess: (message: string) => void
}) {

  /* Define the state variables */
  const [email, setEmail] = useState('')
  const [relationship, setRelationship] = useState<Exclude<Relationship, 'SELF'> | ''>('')
  const [state, setState] = useState<SearchState>('initial')
  const [result, setResult] = useState<ExistingUserSearchResult | null>(null)
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null)
  const [message, setMessage] = useState('')
  const [copyNotice, setCopyNotice] = useState('')

  /* Define the search function */
  const search = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    // Prevent the default form submission behavior
    event.preventDefault()

    // Validate the email address
    if (!email.trim()) {
      setState('error')
      setMessage('Enter an email address.')
      return
    }

    // Validate the email address format
    const emailError = getEmailValidationError(email)
    if (emailError) {
      setState('error')
      setMessage(emailError)
      return
    }

    // Set the state to searching and reset the state variables
    setState('searching')
    setMessage('')
    setResult(null)
    setInvitation(null)
    setCopyNotice('')
    try {
      // Search for the existing user
      const match = await familyApiService.searchExistingUser(email)
      setResult(match)
      if (match.familyLinkStatus === 'ALREADY_LINKED') {
        setState('already-linked')
      } else if (match.familyLinkStatus === 'PENDING') {
        setState('pending')
      } else if (match.accountStatus === 'NOT_REGISTERED') {
        setState('not-registered')
      } else {
        setState('found')
      }
    } catch (caughtError) {
      // Set the state to error and set the error message
      setState('error')
      setMessage(getErrorMessage(caughtError))
    }
  }

  /* Define the create invite function */
  const createInvite = async () => {
    if (state === 'inviting') return
    if (!relationship) {
      setState('error')
      setMessage('Select a relationship.')
      return
    }
    setState('inviting')
    try {
      // Create an invitation
      const created = await familyApiService.createInvitation(email.trim(), relationship)
      setInvitation(created)
      setState('invited')
      onSuccess(`Invitation created for ${created.invitedEmail}.`)
    } catch (caughtError) {
      // Set the state to error and set the error message
      setState('error')
      setMessage(getErrorMessage(caughtError))
    }
  }

  /* Define the copy text function */
  const copyText = async (label: string, value: string) => {
    try {
      // Copy the text to the clipboard
      await navigator.clipboard.writeText(value)
      setCopyNotice(`${label} copied.`)
    } catch {
      setCopyNotice(`Could not copy ${label.toLowerCase()}.`)
    }
  }

  /* Define the mailto href function */
  const mailtoHref = invitation
    ? `mailto:${encodeURIComponent(invitation.invitedEmail)}?subject=${encodeURIComponent(
        'You are invited to join a CanMakan family',
      )}&body=${encodeURIComponent(
        `You have been invited to join a CanMakan family circle.\n\nOpen this link: ${invitation.inviteUrl}\nOr use invite code: ${invitation.inviteCode}\n`,
      )}`
    : undefined

  /* Define the can invite function */
  const canInvite =
    result &&
    (state === 'found' || state === 'not-registered' || state === 'inviting')

  /* Define the render function */
  return (
    <Modal
      title="Invite to Family"
      description="Search by email. Registered or not-yet-registered addresses can receive a shareable invite."
      onClose={onClose}
    >
      <form className="search-form" onSubmit={search}>
        <label htmlFor="existing-user-email">Email address</label>
        <div className="search-form__row">
          <input
            id="existing-user-email"
            type="email"
            value={email}
            placeholder="jamie@example.com"
            onChange={(event) => setEmail(event.target.value)}
            disabled={state === 'searching' || state === 'inviting'}
          />
          <button
            className="button button--primary"
            type="submit"
            disabled={state === 'searching' || state === 'inviting'}
          >
            {state === 'searching' ? 'Searching…' : 'Search'}
          </button>
        </div>
      </form>

      <div className="field-group">
        <label htmlFor="invite-relationship">Relationship to you</label>
        <select
          id="invite-relationship"
          value={relationship}
          onChange={(event) =>
            setRelationship(event.target.value as Exclude<Relationship, 'SELF'> | '')
          }
          disabled={state === 'searching' || state === 'inviting'}
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

      <div className="search-result" aria-live="polite">
        {state === 'initial' && (
          <p>Only a display name, masked email and account status will be shown.</p>
        )}
        {result &&
          ['found', 'not-registered', 'already-linked', 'pending', 'inviting'].includes(
            state,
          ) && (
            <article className="matched-user">
              <span className="avatar">
                {(result.displayName ?? result.maskedEmail).slice(0, 1).toUpperCase()}
              </span>
              <div>
                <strong>
                  {result.displayName ??
                    (result.accountStatus === 'NOT_REGISTERED'
                      ? 'Not registered yet'
                      : 'CanMakan user')}
                </strong>
                <span>{result.maskedEmail}</span>
                <span>Account: {result.accountStatus}</span>
              </div>
              {canInvite ? (
                <button
                  className="button button--primary"
                  type="button"
                  disabled={state === 'inviting' || !relationship}
                  onClick={() => void createInvite()}
                >
                  {state === 'inviting' ? 'Creating invite…' : 'Create invite'}
                </button>
              ) : (
                <span className="status-badge status-badge--warning">
                  {state === 'pending' ? 'Pending invitation' : 'Already in a family'}
                </span>
              )}
            </article>
          )}
        {state === 'invited' && invitation && (
          <div className="notice notice--success">
            <strong>Invitation ready</strong>
            <p>
              Share the link or short code. The invitee joins when they register or
              sign in with this email.
            </p>
            <p>
              <code>{invitation.inviteUrl}</code>
            </p>
            <p>
              Code: <strong>{invitation.inviteCode}</strong>
            </p>
            {copyNotice && <p>{copyNotice}</p>}
            <div className="page-header__actions">
              <button
                className="button button--secondary"
                type="button"
                onClick={() => void copyText('Invite link', invitation.inviteUrl)}
              >
                Copy link
              </button>
              <button
                className="button button--secondary"
                type="button"
                onClick={() => void copyText('Invite code', invitation.inviteCode)}
              >
                Copy code
              </button>
              {mailtoHref && (
                <a className="button button--secondary" href={mailtoHref}>
                  Send via Email
                </a>
              )}
              <button className="button button--primary" type="button" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        )}
        {state === 'error' && (
          <p className="form-message form-message--error" role="alert">
            {message}
          </p>
        )}
      </div>
    </Modal>
  )
}

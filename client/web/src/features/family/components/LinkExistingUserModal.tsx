import { useState, type FormEvent } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { ExistingUserSearchResult } from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { getEmailValidationError } from '../../../shared/validation/email'

type SearchState =
  | 'initial'
  | 'searching'
  | 'found'
  | 'not-found'
  | 'already-linked'
  | 'pending'
  | 'linking'
  | 'success'
  | 'error'

export function LinkExistingUserModal({
  onClose,
  onSuccess,
}: {
  onClose: () => void
  onSuccess: (message: string) => void
}) {
  const [email, setEmail] = useState('')
  const [state, setState] = useState<SearchState>('initial')
  const [result, setResult] = useState<ExistingUserSearchResult | null>(null)
  const [message, setMessage] = useState('')

  const search = async (event: FormEvent) => {
    event.preventDefault()
    if (!email.trim()) {
      setState('error')
      setMessage('Enter an email address.')
      return
    }
    const emailError = getEmailValidationError(email)
    if (emailError) {
      setState('error')
      setMessage(emailError)
      return
    }
    setState('searching')
    setMessage('')
    setResult(null)
    try {
      const match = await familyApiService.searchExistingUser(email)
      if (!match) {
        setState('not-found')
        return
      }
      setResult(match)
      setState(
        match.familyLinkStatus === 'ALREADY_LINKED'
          ? 'already-linked'
          : match.familyLinkStatus === 'PENDING'
            ? 'pending'
            : 'found',
      )
    } catch (caughtError) {
      setState('error')
      setMessage(getErrorMessage(caughtError))
    }
  }

  const linkUser = async () => {
    if (!result || state === 'linking') return
    setState('linking')
    try {
      const linked = await familyApiService.linkExistingUser(result.userId)
      setState('success')
      setMessage(`${linked.profileName} is now linked to this family.`)
      onSuccess(`${linked.profileName} was linked as an existing App User.`)
    } catch (caughtError) {
      setState('error')
      setMessage(getErrorMessage(caughtError))
    }
  }

  return (
    <Modal
      title="Add Existing App User"
      description="Search for a person who already has a registered CanMakan App account."
      onClose={onClose}
    >
      <form className="search-form" onSubmit={search}>
        <label htmlFor="existing-user-email">Registered email address</label>
        <div className="search-form__row">
          <input
            id="existing-user-email"
            type="email"
            value={email}
            placeholder="jamie@example.com"
            onChange={(event) => setEmail(event.target.value)}
            disabled={state === 'searching' || state === 'linking'}
          />
          <button
            className="button button--primary"
            type="submit"
            disabled={state === 'searching' || state === 'linking'}
          >
            {state === 'searching' ? 'Searching…' : 'Search'}
          </button>
        </div>
        <span className="field-hint">
          Demo: jamie@example.com, alicia@example.com, pending@example.com or
          error@demo.test.
        </span>
      </form>

      <div className="search-result" aria-live="polite">
        {state === 'initial' && (
          <p>Only a display name, masked email and account status will be shown.</p>
        )}
        {state === 'not-found' && (
          <div className="notice notice--neutral">
            <strong>No registered user found</strong>
            <p>Check the address or create a separate non-login profile instead.</p>
          </div>
        )}
        {result && ['found', 'already-linked', 'pending', 'linking'].includes(state) && (
          <article className="matched-user">
            <span className="avatar">{result.displayName.slice(0, 1)}</span>
            <div>
              <strong>{result.displayName}</strong>
              <span>{result.maskedEmail}</span>
              <span>Account: {result.accountStatus}</span>
            </div>
            {state === 'found' || state === 'linking' ? (
              <button
                className="button button--primary"
                type="button"
                disabled={state === 'linking'}
                onClick={linkUser}
              >
                {state === 'linking' ? 'Linking…' : 'Confirm link'}
              </button>
            ) : (
              <span className="status-badge status-badge--warning">
                {state === 'pending' ? 'Pending invitation' : 'Already in family'}
              </span>
            )}
          </article>
        )}
        {state === 'success' && (
          <div className="notice notice--success">
            <strong>Existing user linked</strong>
            <p>{message}</p>
            <button className="button button--secondary" type="button" onClick={onClose}>
              Done
            </button>
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

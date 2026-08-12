import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../api/apiErrors'
import type { Portal, Role } from '../api/types'
import { useSession } from '../../features/auth/useSession'
import { familyApiService } from '../../features/family/api/familyApiService'
import { isPasswordWithinBcryptLimit } from '../validation/authFields'
import { getEmailValidationError } from '../validation/email'
import { PasswordField } from './PasswordField'

/**
 * Email/password login form shared by portal entry pages.
 *
 * @author Amelia
 * @author YangMaowei
 */

interface CredentialLoginFormProps {
  portal: Portal
  expectedRole: Role
  destination: '/family' | '/system'
  buttonLabel: string
  buttonClassName: string
  registerPath?: string
}

type InvitationClaimStatus = 'idle' | 'pending' | 'failed' | 'succeeded'

export function CredentialLoginForm({
  portal,
  expectedRole,
  destination,
  buttonLabel,
  buttonClassName,
  registerPath,
}: CredentialLoginFormProps) {
  const [searchParams] = useSearchParams()
  const invitationToken = searchParams.get('invitationToken')?.trim() || undefined
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [invitationClaimStatus, setInvitationClaimStatus] =
    useState<InvitationClaimStatus>('idle')
  const { session, loginWithCredentials, logout, loading } = useSession()
  const navigate = useNavigate()

  const authenticatedForPortal = session?.roles.includes(expectedRole) === true
  const invitationClaimRequired = portal === 'FAMILY' && Boolean(invitationToken)

  // A deep-link login must remain mounted until its authenticated claim finishes.
  if (
    authenticatedForPortal &&
    (!invitationClaimRequired || invitationClaimStatus === 'succeeded')
  ) {
    return <Navigate to={destination} replace />
  }

  const claimInvitation = async () => {
    if (!invitationToken) return true
    setInvitationClaimStatus('pending')
    setError('')
    try {
      await familyApiService.claimInvitation(invitationToken)
      setInvitationClaimStatus('succeeded')
      return true
    } catch (claimError) {
      setInvitationClaimStatus('failed')
      setError(getErrorMessage(claimError))
      return false
    }
  }

  const retryInvitationClaim = async () => {
    if (await claimInvitation()) {
      navigate(destination, { replace: true })
    }
  }

  // Handle the submission of the form
  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    // 1. Prevent the default form submission behavior
    event.preventDefault()

    // 2. Reset the error
    setError('')

    // 3. Validate the form data (mirror backend limits to avoid failed API calls)
    const trimmedEmail = email.trim()
    if (!trimmedEmail || !password) {
      setError('Enter your email and password to continue.')
      return
    }
    const emailError = getEmailValidationError(trimmedEmail)
    if (emailError) {
      setError(emailError)
      return
    }
    if (!isPasswordWithinBcryptLimit(password)) {
      setError('Password must not exceed 72 UTF-8 bytes.')
      return
    }

    // 4. Try to login with the credentials
    try {
      if (invitationClaimRequired) {
        // Set this before session installation so authenticated rendering cannot
        // redirect while the claim request is still pending.
        setInvitationClaimStatus('pending')
      }
      // Login with the credentials
      const authenticated = await loginWithCredentials({
        email: trimmedEmail,
        password,
        portal,
      })
      // If the authenticated user does not have the expected role, logout and set the error
      if (!authenticated.roles.includes(expectedRole)) {
        void logout()
        setInvitationClaimStatus('idle')
        setError('This account cannot access this portal.')
        return
      }
      // If the portal is family and there is an invitation token, claim the invitation
      if (invitationClaimRequired && !(await claimInvitation())) {
        return
      }
      // Navigate to the destination
      navigate(destination, { replace: true })
    } catch (caughtError) {
      // Set the error
      setInvitationClaimStatus('idle')
      setError(getErrorMessage(caughtError))
    }
  }

  // Define the resolved register path
  const resolvedRegisterPath =
    registerPath && invitationToken
      ? `${registerPath}?invitationToken=${encodeURIComponent(invitationToken)}`
      : registerPath

  // Render the component
  return (
    <>
      {authenticatedForPortal && invitationClaimRequired ? (
        <div className="invitation-claim-status">
          {error && (
            <p className="form-message form-message--error" role="alert">
              {error}
            </p>
          )}
          <button
            className={`button ${buttonClassName} button--full`}
            type="button"
            disabled={invitationClaimStatus === 'pending'}
            onClick={() => void retryInvitationClaim()}
          >
            {invitationClaimStatus === 'pending'
              ? 'Claiming invitation…'
              : invitationClaimStatus === 'failed'
                ? 'Retry invitation claim'
                : 'Claim invitation'}
          </button>
        </div>
      ) : (
        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <label htmlFor={`${portal.toLowerCase()}-email`}>Email</label>
          <input
            id={`${portal.toLowerCase()}-email`}
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={loading}
          />
          <PasswordField
            id={`${portal.toLowerCase()}-password`}
            label="Password"
            autoComplete="current-password"
            value={password}
            onChange={setPassword}
            disabled={loading}
          />
          {error && (
            <p className="form-message form-message--error" role="alert">
              {error}
            </p>
          )}
          <button
            className={`button ${buttonClassName} button--full`}
            type="submit"
            disabled={loading || invitationClaimStatus === 'pending'}
          >
            {loading ? 'Signing in…' : buttonLabel}
          </button>
        </form>
      )}
      {!authenticatedForPortal && resolvedRegisterPath ? (
        <p className="login-card__footer">
          New to CanMakan? <Link to={resolvedRegisterPath}>Create an account</Link>
        </p>
      ) : null}
      <p className="login-card__security">
        Your sign-in details are handled securely and are not displayed after
        you sign in.
      </p>
    </>
  )
}

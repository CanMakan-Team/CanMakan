import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/apiErrors'
import type { Portal, Role } from '../api/types'
import { useSession } from '../../features/auth/useSession'
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

export function CredentialLoginForm({
  portal,
  expectedRole,
  destination,
  buttonLabel,
  buttonClassName,
  registerPath,
}: CredentialLoginFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const { session, loginWithCredentials, logout, loading } = useSession()
  const navigate = useNavigate()

  // If the session has the expected role, navigate to the destination
  if (session?.roles.includes(expectedRole)) {
    return <Navigate to={destination} replace />
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
    // 5. If the login is successful, check if the session has the expected role
    // 6. If the session has the expected role, navigate to the destination
    // 7. If the session does not have the expected role, logout and set the error
    try {
      const authenticated = await loginWithCredentials({
        email: trimmedEmail,
        password,
        portal,
      })
      if (!authenticated.roles.includes(expectedRole)) {
        logout()
        setError('This account cannot access this portal.')
        return
      }
      navigate(destination, { replace: true })
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    }
  }

  return (
    <>
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
          disabled={loading}
        >
          {loading ? 'Signing in…' : buttonLabel}
        </button>
      </form>
      {registerPath ? (
        <p className="login-card__footer">
          New to CanMakan? <Link to={registerPath}>Create an account</Link>
        </p>
      ) : null}
      <p className="login-card__security">
        Credentials are checked against the database. API calls use a Bearer
        access token from this session.
      </p>
    </>
  )
}

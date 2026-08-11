import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../shared/api/apiErrors'
import { useSession } from '../features/auth/useSession'
import { PasswordField } from '../shared/ui/PasswordField'
import { getRegistrationPasswordError } from '../shared/validation/authFields'
import { getEmailValidationError } from '../shared/validation/email'

/**
 * UC18 family-portal registration. Matches FamilyLoginPage theme.
 * Optional UC9 invitationToken joins the invitee's family after register.
 * 
 * @author Amelia
 * @author YangMaowei
 */

/* Define the FamilyRegisterPage component */
export function FamilyRegisterPage() {
  const [searchParams] = useSearchParams()
  /* Define the invitation token */
  const invitationToken = searchParams.get('invitationToken')?.trim() || undefined
  /* Define the state variables */
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState('')
  const [submitError, setSubmitError] = useState('')
  /* Define the session and navigation */
  const { session, register, loading } = useSession()
  const navigate = useNavigate()

  /* If the session has the family admin role, navigate to the family page */
  if (session?.roles.includes('ROLE_FAMILY_ADMIN')) {
    return <Navigate to="/family" replace />
  }

  /* Define the clear validation error function */
  const clearValidationError = () => {
    if (validationError) setValidationError('')
  }

  /* Define the handle submit function */
  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitError('')

    // Validate the form data (mirror backend limits to avoid failed API calls)
    const trimmedName = name.trim()
    const trimmedEmail = email.trim()

    if (trimmedName.length < 3 || trimmedName.length > 100) {
      setValidationError('Name must be between 3 and 100 characters.')
      return
    }
    const emailError = getEmailValidationError(trimmedEmail)
    if (emailError) {
      setValidationError(emailError)
      return
    }
    const passwordError = getRegistrationPasswordError(password)
    if (passwordError) {
      setValidationError(passwordError)
      return
    }
    if (password !== confirmPassword) {
      setValidationError('Passwords do not match.')
      return
    }
    setValidationError('')

    // Register without establishing an authenticated session.
    // On success, return to sign-in.
    // On error, set the submit error
    try {
      await register({
        name: trimmedName,
        email: trimmedEmail,
        password,
        invitationToken,
      })
      navigate('/family-login', { replace: true })
    } catch (caughtError) {
      setSubmitError(getErrorMessage(caughtError))
    }
  }

  /* Define the render function */
  return (
    <main className="login-page login-page--family">
      <div className="login-composition login-composition--family">
        <section
          className="family-login-introduction"
          aria-labelledby="family-register-intro-title"
        >
          <div className="login-brand">
            <span className="brand-mark" aria-hidden="true">
              CM
            </span>
            <strong>CanMakan</strong>
          </div>
          <span className="portal-icon portal-icon--family" aria-hidden="true">
            ♡
          </span>
          <p className="eyebrow">Family Portal</p>
          <h1 id="family-register-intro-title">Create your CanMakan account.</h1>
          <p>
            {invitationToken
              ? 'Register with the invited email to join the family circle automatically.'
              : 'Register with your name, email, and password. You can create a family circle after sign-in — registration does not start a household by itself.'}
          </p>
        </section>

        <section className="login-card" aria-labelledby="family-register-title">
          <p className="eyebrow">Get started</p>
          <h2 id="family-register-title">Create account</h2>
          <p>Use an email that is not already registered.</p>

          <form onSubmit={(event) => void handleSubmit(event)} noValidate>
            <label htmlFor="register-name">Full name</label>
            <input
              id="register-name"
              type="text"
              autoComplete="name"
              maxLength={100}
              value={name}
              onChange={(event) => {
                setName(event.target.value)
                clearValidationError()
              }}
              disabled={loading}
            />
            <label htmlFor="register-email">Email</label>
            <input
              id="register-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value)
                clearValidationError()
              }}
              disabled={loading}
            />
            <PasswordField
              id="register-password"
              label="Password"
              autoComplete="new-password"
              value={password}
              onChange={(next) => {
                setPassword(next)
                clearValidationError()
              }}
              disabled={loading}
            />
            <PasswordField
              id="register-confirm-password"
              label="Confirm password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(next) => {
                setConfirmPassword(next)
                clearValidationError()
              }}
              disabled={loading}
            />
            {(validationError || submitError) && (
              <p className="form-message form-message--error" role="alert">
                {validationError || submitError}
              </p>
            )}
            <button
              className="button button--primary button--full"
              type="submit"
              disabled={loading}
            >
              {loading ? 'Creating account…' : 'Create account'}
            </button>
          </form>

          <p className="login-card__footer">
            Already have an account?{' '}
            <Link
              to={
                invitationToken
                  ? `/family-login?invitationToken=${encodeURIComponent(invitationToken)}`
                  : '/family-login'
              }
            >
              Sign in
            </Link>
          </p>
        </section>
      </div>
    </main>
  )
}

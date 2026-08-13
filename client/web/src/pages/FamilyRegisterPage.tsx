import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../shared/api/apiErrors'
import { pendingRegistrationOnboardingStore } from '../features/auth/pendingRegistrationOnboardingStore'
import { useSession } from '../features/auth/useSession'
import { PasswordField } from '../shared/ui/PasswordField'
import { getRegistrationPasswordError } from '../shared/validation/authFields'
import { getEmailValidationError } from '../shared/validation/email'
import { getProfileNameError } from '../shared/validation/profileFields'

/** UC18 account registration followed by the authoritative UC19 login flow. */
export function FamilyRegisterPage() {
  const [searchParams] = useSearchParams()
  const invitationToken = searchParams.get('invitationToken')?.trim() || undefined
  const [profileName, setProfileName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [accountCreated, setAccountCreated] = useState(false)
  const [showLoginAction, setShowLoginAction] = useState(false)
  const { session, registerAndLogin, loading } = useSession()
  const navigate = useNavigate()

  if (session?.roles.includes('ROLE_APP_USER')) {
    return (
      <Navigate
        to={
          pendingRegistrationOnboardingStore.peekForEmail(session.email)
            ? '/family/setup-profile'
            : '/family'
        }
        replace
      />
    )
  }

  const familyLoginPath = () => {
    const parameters = new URLSearchParams()
    const normalizedEmail = email.trim()
    if (normalizedEmail) parameters.set('email', normalizedEmail)
    if (invitationToken) parameters.set('invitationToken', invitationToken)
    const query = parameters.toString()
    return query ? `/family-login?${query}` : '/family-login'
  }

  const clearValidationError = () => {
    if (validationError) setValidationError('')
  }

  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (accountCreated) return
    setSubmitError('')
    setShowLoginAction(false)

    const trimmedProfileName = profileName.trim()
    const trimmedEmail = email.trim()
    const profileNameError = getProfileNameError(trimmedProfileName)
    if (profileNameError) {
      setValidationError(profileNameError)
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

    // Store only non-secret onboarding data before login installs the session.
    pendingRegistrationOnboardingStore.request({
      email: trimmedEmail,
      profileName: trimmedProfileName,
      invitationToken,
    })
    try {
      const result = await registerAndLogin({ email: trimmedEmail, password })
      setAccountCreated(true)
      setPassword('')
      setConfirmPassword('')
      if (result.status === 'authenticated') {
        navigate('/family/setup-profile', { replace: true })
      } else {
        setSubmitError(
          'Your account was created, but automatic sign-in failed. Log in to continue.',
        )
        setShowLoginAction(true)
      }
    } catch (caughtError) {
      pendingRegistrationOnboardingStore.clear()
      setSubmitError(getErrorMessage(caughtError))
      setShowLoginAction(caughtError instanceof ApiError && caughtError.status === 409)
    }
  }

  return (
    <main className="login-page login-page--family">
      <div className="login-composition login-composition--family">
        <section
          className="family-login-introduction"
          aria-labelledby="family-register-intro-title"
        >
          <div className="login-brand">
            <span className="brand-mark" aria-hidden="true">CM</span>
            <strong>CanMakan</strong>
          </div>
          <p className="eyebrow">Family Portal</p>
          <h1 id="family-register-intro-title">Create your CanMakan account.</h1>
          <p>
            Create your account, then optionally set up one personal dietary profile.
            You can complete dietary setup later.
          </p>
        </section>

        <section className="login-card" aria-labelledby="family-register-title">
          <p className="eyebrow">Get started</p>
          <h2 id="family-register-title">Create account</h2>
          <p>Use an email that is not already registered.</p>

          <form onSubmit={(event) => void handleSubmit(event)} noValidate>
            <label htmlFor="register-profile-name">Profile Name</label>
            <input
              id="register-profile-name"
              autoComplete="name"
              value={profileName}
              maxLength={100}
              onChange={(event) => {
                setProfileName(event.target.value)
                clearValidationError()
              }}
              disabled={loading || accountCreated}
            />
            <p>This name is used only if you choose to create your personal dietary profile.</p>

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
              disabled={loading || accountCreated}
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
              disabled={loading || accountCreated}
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
              disabled={loading || accountCreated}
            />
            {validationError || submitError ? (
              <div className="form-message form-message--error" role="alert">
                <p>{validationError || submitError}</p>
                {showLoginAction ? <Link to={familyLoginPath()}>Log in here</Link> : null}
              </div>
            ) : null}
            <button
              className="button button--primary button--full"
              type="submit"
              disabled={loading || accountCreated}
            >
              {loading ? 'Creating account…' : accountCreated ? 'Account created' : 'Create account'}
            </button>
          </form>

          <p className="login-card__footer">
            Already have an account? <Link to={familyLoginPath()}>Sign in</Link>
          </p>
        </section>
      </div>
    </main>
  )
}

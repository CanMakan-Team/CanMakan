import { useEffect, useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../shared/api/apiErrors'
import { pendingRegistrationOnboardingStore } from '../features/auth/pendingRegistrationOnboardingStore'
import { useSession } from '../features/auth/useSession'
import { familyApiService } from '../features/family/api/familyApiService'
import { CanMakanMascot, LoginBrand } from '../shared/ui/CanMakanMascot'
import { PasswordField } from '../shared/ui/PasswordField'
import { getRegistrationPasswordError } from '../shared/validation/authFields'
import { getEmailValidationError } from '../shared/validation/email'
import { FAMILY_ROOT_PATH, ME_SETUP_PROFILE_PATH, USER_LOGIN_PATH } from '../app/userPortalPaths'

/** UC18 account registration followed by the authoritative UC19 login flow. */
export function UserRegisterPage() {
  const [searchParams] = useSearchParams()
  const invitationToken = searchParams.get('invitationToken')?.trim() || undefined
  const [email, setEmail] = useState('')
  const [emailLocked, setEmailLocked] = useState(false)
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [accountCreated, setAccountCreated] = useState(false)
  const [showLoginAction, setShowLoginAction] = useState(false)
  const { session, registerAndLogin, loading } = useSession()
  const navigate = useNavigate()

  useEffect(() => {
    if (!invitationToken) return
    let cancelled = false
    void familyApiService.previewInvitation(invitationToken).then((preview) => {
      if (cancelled || !preview?.invitedEmail) return
      setEmail(preview.invitedEmail)
      setEmailLocked(true)
    }).catch(() => {
      // Leave the email editable when the invite cannot be loaded.
    })
    return () => {
      cancelled = true
    }
  }, [invitationToken])

  if (session?.roles.includes('ROLE_APP_USER')) {
    return (
      <Navigate
        to={
          pendingRegistrationOnboardingStore.peekForEmail(session.email)
            ? ME_SETUP_PROFILE_PATH
            : FAMILY_ROOT_PATH
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
    return query ? `${USER_LOGIN_PATH}?${query}` : USER_LOGIN_PATH
  }

  const clearValidationError = () => {
    if (validationError) setValidationError('')
  }

  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (accountCreated) return
    setSubmitError('')
    setShowLoginAction(false)

    const trimmedEmail = email.trim()
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
      invitationToken,
    })
    try {
      const result = await registerAndLogin({
        email: trimmedEmail,
        password,
        invitationToken,
      })
      setAccountCreated(true)
      setPassword('')
      setConfirmPassword('')
      if (result.status === 'authenticated') {
        if (invitationToken) {
          // Claim the invitation immediately while we still have the token and an
          // authenticated session, rather than deferring it to the dietary-profile
          // setup screen's exit actions. That screen renders inside the full portal
          // sidebar, so a new invitee could navigate away (e.g. straight to "Family
          // Circle") before ever triggering the claim, leaving them unlinked from
          // the family they were invited to.
          try {
            await familyApiService.claimInvitation(invitationToken)
            pendingRegistrationOnboardingStore.request({
              email: trimmedEmail,
              invitationToken: undefined,
            })
          } catch {
            // Leave the invitation token in the pending onboarding record so the
            // dietary-profile setup screen retries the claim (via finishPath) when
            // the user finishes or defers that step.
          }
        }
        navigate(ME_SETUP_PROFILE_PATH, { replace: true })
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
          <LoginBrand />
          <CanMakanMascot pose="wave" size="large" className="login-greeting-mascot" />
          <p className="eyebrow">Welcome</p>
          <h1 id="family-register-intro-title">Glad you're here. Let's get you an account.</h1>
          <p>
            Set up your dietary profile later, at your own pace. Family Circle is
            optional when you need household tools.
          </p>
        </section>

        <section className="login-card" aria-labelledby="family-register-title">
          <p className="eyebrow">Get started</p>
          <h2 id="family-register-title">Create account</h2>
          <p>Use an email that is not already registered.</p>

          <form onSubmit={(event) => void handleSubmit(event)} noValidate>
            <label htmlFor="register-email">Email</label>
            <input
              id="register-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => {
                if (emailLocked) return
                setEmail(event.target.value)
                clearValidationError()
              }}
              disabled={loading || accountCreated || emailLocked}
              readOnly={emailLocked}
            />
            {emailLocked ? (
              <p>This invitation was sent to this email.</p>
            ) : null}
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

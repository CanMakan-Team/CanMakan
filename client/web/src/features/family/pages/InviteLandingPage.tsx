import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { useSession } from '../../auth/useSession'
import { familyApiService } from '../api/familyApiService'

/**
 * Minimal UC9 invite landing: preserves token and routes to register/login,
 * or claims immediately when already signed in.
 * 
 * @author Amelia
 */
export function InviteLandingPage() {
  const { token } = useParams<{ token: string }>()
  const { session } = useSession()
  const navigate = useNavigate()
  const [claimError, setClaimError] = useState('')

  /* Define the use effect hook */
  // Claim the invitation when the token is present and the session is authenticated
  // On success, navigate to the family page
  // On error, set the claim error
  useEffect(() => {
    if (!token || !session) {
      return
    }
    let cancelled = false
    void familyApiService
      .claimInvitation(token)
      .then(() => {
        if (!cancelled) {
          navigate('/family', { replace: true })
        }
      })
      .catch((caughtError: unknown) => {
        if (!cancelled) {
          setClaimError(getErrorMessage(caughtError))
        }
      })
    return () => {
      cancelled = true
    }
  }, [token, session, navigate])

  /* If the token is not present, navigate to the family register page */
  if (!token) {
    return <Navigate to="/family-register" replace />
  }

  /* If the session is authenticated, render the joining family page */
  if (session) {
    return (
      <main className="login-page login-page--family">
        <section className="login-card">
          <h1>Joining family…</h1>
          {!claimError && <p>Accepting your invitation.</p>}
          {claimError && (
            <p className="form-message form-message--error" role="alert">
              {claimError}
            </p>
          )}
          {claimError && (
            <Link className="button button--primary" to="/family">
              Continue to family
            </Link>
          )}
        </section>
      </main>
    )
  }

  /* Define the register and login paths */
  const registerPath = `/family-register?invitationToken=${encodeURIComponent(token)}`
  const loginPath = `/family-login?invitationToken=${encodeURIComponent(token)}`

  /* Define the render function */
  return (
    <main className="login-page login-page--family">
      <section className="login-card">
        <p className="eyebrow">Family invitation</p>
        <h1>You are invited to join a CanMakan family.</h1>
        <p>
          Create an account with the invited email, or sign in if you already have
          one. The invitation is claimed automatically after authentication.
        </p>
        <div className="page-header__actions">
          <Link className="button button--primary" to={registerPath}>
            Create account
          </Link>
          <Link className="button button--secondary" to={loginPath}>
            Sign in
          </Link>
        </div>
      </section>
    </main>
  )
}

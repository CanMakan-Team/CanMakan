import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { useSession } from '../../auth/useSession'
import { familyApiService } from '../api/familyApiService'
import {
  androidInviteIntentUrl,
  canmakanInviteDeepLink,
  isAndroidUserAgent,
  isMobileUserAgent,
  preferWebInvite,
  webInviteStayUrl,
} from '../lib/inviteAppHandoff'

/**
 * UC9 invite landing: desktop stays on web; Android opens the native app.
 * {@code ?web=1} skips the app handoff when the store is missing or the user
 * chooses to continue in the browser.
 *
 * @author Amelia
 */
export function InviteLandingPage() {
  const { token } = useParams<{ token: string }>()
  const { session } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [claimError, setClaimError] = useState('')
  const stayOnWeb = preferWebInvite(location.search)
  const [userAgent] = useState(() =>
    typeof navigator === 'undefined' ? '' : navigator.userAgent,
  )
  const androidClient = isAndroidUserAgent(userAgent)
  const mobileClient = isMobileUserAgent(userAgent)
  const handoffToApp = Boolean(token) && androidClient && !stayOnWeb

  useEffect(() => {
    if (!token || !handoffToApp) {
      return
    }
    const fallback = webInviteStayUrl(window.location.origin, token)
    window.location.replace(androidInviteIntentUrl(token, fallback))
  }, [token, handoffToApp])

  useEffect(() => {
    if (!token || !session || handoffToApp) {
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
  }, [token, session, navigate, handoffToApp])

  if (!token) {
    return <Navigate to="/family-register" replace />
  }

  const appLink = canmakanInviteDeepLink(token)
  const stayPath = `/invite/${encodeURIComponent(token)}?web=1`

  if (handoffToApp || (mobileClient && !stayOnWeb && !session)) {
    return (
      <main className="login-page login-page--family">
        <section className="login-card">
          <p className="eyebrow">Family invitation</p>
          <h1>{handoffToApp ? 'Opening CanMakan…' : 'Continue in CanMakan'}</h1>
          <p>
            {handoffToApp
              ? 'This invitation opens in the CanMakan app. If nothing happens, continue below.'
              : 'On a phone, open this invitation in the CanMakan app. You can also join on the web.'}
          </p>
          <div className="page-header__actions">
            <a className="button button--primary" href={appLink}>
              Open CanMakan app
            </a>
            <Link className="button button--secondary" to={stayPath}>
              Continue on the web
            </Link>
          </div>
        </section>
      </main>
    )
  }

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

  const registerPath = `/family-register?invitationToken=${encodeURIComponent(token)}`
  const loginPath = `/family-login?invitationToken=${encodeURIComponent(token)}`

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

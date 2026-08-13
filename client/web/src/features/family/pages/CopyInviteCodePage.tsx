import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

/**
 * Opens from the invitation email so tapping the invite code can copy it.
 * Email clients do not run JavaScript; this page performs the copy in the browser.
 *
 * @author Amelia
 */
export function CopyInviteCodePage() {
  const [searchParams] = useSearchParams()
  const code = searchParams.get('code')?.trim() ?? ''
  const [status, setStatus] = useState<'copying' | 'copied' | 'manual'>(
    code ? 'copying' : 'manual',
  )

  useEffect(() => {
    if (!code) {
      return
    }
    let cancelled = false
    void navigator.clipboard
      .writeText(code)
      .then(() => {
        if (!cancelled) {
          setStatus('copied')
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('manual')
        }
      })
    return () => {
      cancelled = true
    }
  }, [code])

  return (
    <main className="login-page login-page--family">
      <section className="login-card">
        <p className="eyebrow">Invite code</p>
        <h1>{code || 'No invite code in this link.'}</h1>
        {code && status === 'copied' && (
          <p>Copied to your clipboard. Paste it in the CanMakan app to join.</p>
        )}
        {code && status === 'copying' && <p>Copying your invite code…</p>}
        {code && status === 'manual' && (
          <p>Select the code above and copy it, then paste it in the CanMakan app.</p>
        )}
        {!code && (
          <p>Open the invitation email again and tap the highlighted code.</p>
        )}
        <div className="page-header__actions">
          <Link className="button button--primary" to="/family-login">
            Sign in
          </Link>
          <Link className="button button--secondary" to="/family-register">
            Create account
          </Link>
        </div>
      </section>
    </main>
  )
}

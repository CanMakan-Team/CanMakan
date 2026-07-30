import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/apiErrors'
import type { Portal, Role } from '../api/types'
import { useSession } from '../../features/auth/useSession'

interface PrototypeLoginFormProps {
  portal: Portal
  expectedRole: Role
  destination: '/family' | '/system'
  email: string
  buttonLabel: string
  buttonClassName: string
  fieldId: string
}

export function PrototypeLoginForm({
  portal,
  expectedRole,
  destination,
  email,
  buttonLabel,
  buttonClassName,
  fieldId,
}: PrototypeLoginFormProps) {
  const [error, setError] = useState('')
  const { session, login, loading } = useSession()
  const navigate = useNavigate()

  if (session?.roles.includes(expectedRole)) {
    return <Navigate to={destination} replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await login(portal)
      navigate(destination, { replace: true })
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    }
  }

  return (
    <>
      <div className="demo-notice">
        <strong>Prototype Login</strong>
        <span>
          Demo access uses a browser-only mock session. It is not production
          authentication.
        </span>
      </div>
      <form onSubmit={handleSubmit}>
        <label htmlFor={fieldId}>Demo account</label>
        <input id={fieldId} type="email" value={email} readOnly />
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
          {loading ? 'Opening portal…' : buttonLabel}
        </button>
      </form>
      <p className="login-card__security">
        Frontend route guards support this prototype. Spring Security must
        enforce the same RBAC rules on every production API endpoint.
      </p>
    </>
  )
}

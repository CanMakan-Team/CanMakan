import { useId, useState } from 'react'

type PasswordFieldProps = {
  id?: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete?: string
  disabled?: boolean
}

/**
 * Password input with show/hide toggle (eye icon).
 */
export function PasswordField({
  id: idProp,
  label,
  value,
  onChange,
  autoComplete = 'current-password',
  disabled = false,
}: PasswordFieldProps) {
  const generatedId = useId()
  const id = idProp ?? generatedId
  const [visible, setVisible] = useState(false)

  return (
    <>
      <label htmlFor={id}>{label}</label>
      <div className="password-field">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={disabled}
        />
        <button
          type="button"
          className="password-field__toggle"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          disabled={disabled}
        >
          {visible ? <EyeOffIcon /> : <EyeIcon />}
        </button>
      </div>
    </>
  )
}

function EyeIcon() {
  return (
    <svg
      className="password-field__icon"
      viewBox="0 0 24 24"
      width="20"
      height="20"
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        d="M12 5c-5.5 0-9.7 4.1-11 7 1.3 2.9 5.5 7 11 7s9.7-4.1 11-7c-1.3-2.9-5.5-7-11-7zm0 11.5A4.5 4.5 0 1 1 12 7.5a4.5 4.5 0 0 1 0 9zm0-2.2a2.3 2.3 0 1 0 0-4.6 2.3 2.3 0 0 0 0 4.6z"
      />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg
      className="password-field__icon"
      viewBox="0 0 24 24"
      width="20"
      height="20"
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        d="M3.3 2.2 2.1 3.4l3.1 3.1C3.4 8 1.8 9.9 1 12c1.3 2.9 5.5 7 11 7 2.1 0 4-.6 5.6-1.5l3.3 3.3 1.2-1.2L3.3 2.2zM12 17c-4.3 0-7.7-3.1-9-5 .7-1.1 2-2.6 3.7-3.7l1.8 1.8A4.5 4.5 0 0 0 12 16.5V17zm0-12c5.5 0 9.7 4.1 11 7-.5 1.1-1.4 2.5-2.7 3.7l-1.5-1.5c.9-.9 1.6-1.9 2.1-2.2-1.3-2-4.7-5-9-5-.7 0-1.4.1-2 .2L8.3 5.7C9.4 5.3 10.7 5 12 5zm.1 3.5c.3-.1.6-.1.9-.1a4.5 4.5 0 0 1 4.5 4.5c0 .3 0 .6-.1.9l-5.3-5.3z"
      />
    </svg>
  )
}

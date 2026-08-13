import { useEffect, useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { pendingRegistrationOnboardingStore } from '../../auth/pendingRegistrationOnboardingStore'
import { useSession } from '../../auth/useSession'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { getProfileNameError } from '../../../shared/validation/profileFields'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
  type ProfileRestrictionSeverity,
} from '../api/selfProfileApiService'

const RELIGIOUS_CATEGORY = 'RELIGIOUS'

function finishPath(invitationToken?: string) {
  return invitationToken
    ? `/invite/${encodeURIComponent(invitationToken)}`
    : '/family/personal'
}

export function SelfProfileSetupPage() {
  const { session } = useSession()
  const sessionEmail = session?.email
  const navigate = useNavigate()
  const pending = session
    ? pendingRegistrationOnboardingStore.peekForEmail(session.email)
    : null
  const [profileName, setProfileName] = useState(pending?.profileName ?? '')
  const [catalog, setCatalog] = useState<DietaryRestrictionOption[]>([])
  const [selected, setSelected] = useState<Record<number, ProfileRestrictionSeverity>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!sessionEmail) return
    let active = true
    selfProfileApiService
      .getCatalog()
      .then((options) => {
        if (active) setCatalog(options)
      })
      .catch((caughtError: unknown) => {
        if (active) setError(getErrorMessage(caughtError))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [sessionEmail])

  const groupedCatalog = useMemo(
    () =>
      Object.entries(
        catalog.reduce<Record<string, DietaryRestrictionOption[]>>((groups, option) => {
          const category = option.category || 'OTHER'
          groups[category] = [...(groups[category] ?? []), option]
          return groups
        }, {}),
      ),
    [catalog],
  )

  if (!session) return <Navigate to="/family-login" replace />

  const toggle = (option: DietaryRestrictionOption) => {
    setSelected((current) => {
      const next = { ...current }
      if (next[option.id]) {
        delete next[option.id]
      } else {
        if (option.category === RELIGIOUS_CATEGORY) {
          catalog
            .filter((item) => item.category === RELIGIOUS_CATEGORY)
            .forEach((item) => delete next[item.id])
        }
        next[option.id] = 'STRICT_AVOID'
      }
      return next
    })
    setError('')
  }

  const setUpLater = () => {
    pendingRegistrationOnboardingStore.clear()
    navigate(finishPath(pending?.invitationToken), {
      replace: true,
      state: { profileSetup: 'deferred' },
    })
  }

  const save = async () => {
    if (saving) return
    const normalizedProfileName = profileName.trim()
    const profileNameError = getProfileNameError(normalizedProfileName)
    if (profileNameError) {
      setError(profileNameError)
      return
    }
    if (Object.keys(selected).length === 0) {
      setError('Select at least one dietary restriction or set up your profile later.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await selfProfileApiService.createSelfProfile(normalizedProfileName, selected)
      pendingRegistrationOnboardingStore.clear()
      navigate(finishPath(pending?.invitationToken), {
        replace: true,
        state: { profileSetup: 'created' },
      })
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page-shell">
      <section className="page-card" aria-labelledby="self-profile-setup-title">
        <p className="eyebrow">Optional dietary setup</p>
        <h1 id="self-profile-setup-title">Set up your dietary profile</h1>
        <p>You can complete this later. Setting it up now helps personalise future scans.</p>

        <div className="field-group">
          <label htmlFor="setup-profile-name">Profile Name</label>
          <input
            id="setup-profile-name"
            value={profileName}
            maxLength={100}
            readOnly={Boolean(pending)}
            onChange={(event) => {
              setProfileName(event.target.value)
              setError('')
            }}
          />
          <p>This is the name for your personal dietary profile.</p>
          {!pending ? <p>Enter the Profile Name you want to use.</p> : null}
        </div>

        {loading ? <p role="status">Loading dietary options…</p> : null}
        {!loading
          ? groupedCatalog.map(([category, options]) => (
              <fieldset key={category} className="restriction-picker">
                <legend>{category.replaceAll('_', ' ')}</legend>
                <div className="checkbox-grid">
                  {options.map((option) => (
                    <label className="check-card" key={option.id}>
                      <input
                        type="checkbox"
                        checked={Boolean(selected[option.id])}
                        disabled={saving}
                        onChange={() => toggle(option)}
                      />
                      <span>{option.displayName}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))
          : null}

        {error ? (
          <p className="form-message form-message--error" role="alert">
            {error}
          </p>
        ) : null}

        <div className="modal__actions">
          <button
            className="button button--secondary"
            type="button"
            disabled={saving}
            onClick={setUpLater}
          >
            Set Up Later
          </button>
          <button
            className="button button--primary"
            type="button"
            disabled={saving || loading}
            onClick={() => void save()}
          >
            {saving ? 'Saving profile…' : 'Save Profile'}
          </button>
        </div>
      </section>
    </div>
  )
}

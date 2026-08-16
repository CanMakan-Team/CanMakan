import { useEffect, useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { pendingRegistrationOnboardingStore } from '../../auth/pendingRegistrationOnboardingStore'
import { useSession } from '../../auth/useSession'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
import { getProfileNameError } from '../../../shared/validation/profileFields'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
  type ProfileRestrictionSeverity,
  type SelfProfileResponse,
} from '../api/selfProfileApiService'

const RELIGIOUS_CATEGORY = 'RELIGIOUS'

// Fixed display order for dietary restriction sections, regardless of the
// order categories are returned in from the catalog API.
const CATEGORY_DISPLAY_ORDER = ['RELIGIOUS', 'ALLERGEN', 'DIET']

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
  // Severity actually persisted for each restriction, as loaded from the
  // server, keyed by restriction id. Used to resend a restriction's original
  // severity (e.g. PREFERENCE, set via the family admin flow) when the user
  // never touched its checkbox, instead of the STRICT_AVOID/INTOLERANCE
  // approximation this page displays it as.
  const [persistedSeverities, setPersistedSeverities] = useState<Record<number, string>>({})
  // Restriction ids the user has explicitly toggled during this session. Only
  // these should be sent using the on/off severity this form can represent;
  // untouched rows keep resending their original persisted severity.
  const [touchedIds, setTouchedIds] = useState<Set<number>>(new Set())
  const [existingProfileId, setExistingProfileId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  useEffect(() => {
    if (!sessionEmail) return
    let active = true

    // Joining or creating a family auto-provisions a placeholder SELF profile
    // row server-side (see FamilyService.applyInvitationClaim / createFamily),
    // so even a brand-new registrant (tracked via `pending`) can already have
    // one by the time they reach this page. Always look it up; a 404 just
    // means none has been created yet.
    const fetchExistingProfile: Promise<SelfProfileResponse | null> =
      selfProfileApiService.getSelfProfile().catch((caughtError: unknown) => {
        if (caughtError instanceof ApiError && caughtError.status === 404) {
          return null
        }
        throw caughtError
      })

    Promise.all([selfProfileApiService.getCatalog(), fetchExistingProfile])
      .then(([options, existingProfile]) => {
        if (!active) return
        setCatalog(options)
        if (existingProfile) {
          setExistingProfileId(existingProfile.profileId)
          // Prefer the name the user typed at registration over whatever is
          // currently persisted: on a fresh invite/registration flow the
          // persisted value is just an auto-generated placeholder (the
          // email's local part) until this page's first save overwrites it.
          setProfileName(pending?.profileName || existingProfile.profileName)
          setPersistedSeverities(
            Object.entries(existingProfile.restrictions).reduce<Record<number, string>>(
              (accumulator, [restrictionId, severity]) => {
                accumulator[Number(restrictionId)] = severity
                return accumulator
              },
              {},
            ),
          )
          setTouchedIds(new Set())
          setSelected(
            Object.entries(existingProfile.restrictions).reduce<
              Record<number, ProfileRestrictionSeverity>
            >((accumulator, [restrictionId, severity]) => {
              // This page only offers a plain on/off toggle per restriction, not a
              // severity picker, so it can only submit STRICT_AVOID or INTOLERANCE
              // (the two the backend accepts from self-setup). A restriction saved
              // elsewhere with a different severity (e.g. PREFERENCE, set via the
              // family admin flow) still shows as checked here, defaulting to
              // STRICT_AVOID rather than resending a severity this form can't
              // represent and getting rejected on save. The original severity is
              // preserved separately in `persistedSeverities` and resent as-is on
              // save unless the user actually toggles this restriction.
              accumulator[Number(restrictionId)] =
                severity === 'INTOLERANCE' ? 'INTOLERANCE' : 'STRICT_AVOID'
              return accumulator
            }, {}),
          )
        }
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
  }, [sessionEmail, pending])

  const groupedCatalog = useMemo(
    () =>
      Object.entries(
        catalog.reduce<Record<string, DietaryRestrictionOption[]>>((groups, option) => {
          const category = option.category || 'OTHER'
          groups[category] = [...(groups[category] ?? []), option]
          return groups
        }, {}),
      ).sort(([categoryA], [categoryB]) => {
        const indexA = CATEGORY_DISPLAY_ORDER.indexOf(categoryA)
        const indexB = CATEGORY_DISPLAY_ORDER.indexOf(categoryB)
        // Categories not in the fixed order list are pushed to the end,
        // after the known sections, in their original order.
        if (indexA === -1 && indexB === -1) return 0
        if (indexA === -1) return 1
        if (indexB === -1) return -1
        return indexA - indexB
      }),
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
    // Track that the user explicitly changed this restriction's checkbox, so
    // save() knows to send the new on/off severity for it rather than
    // resending whatever severity was originally persisted.
    setTouchedIds((current) => new Set(current).add(option.id))
    setError('')
    setSuccessMessage('')
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
      setSuccessMessage('')
      return
    }
    if (Object.keys(selected).length === 0) {
      setError('Select at least one dietary restriction or set up your profile later.')
      setSuccessMessage('')
      return
    }
    setSaving(true)
    setError('')
    setSuccessMessage('')
    // This is the user's first-ever save whenever they're mid-onboarding
    // (`pending` set) or no profile existed before this save — that covers a
    // plain first-time setup as well as filling in an auto-provisioned
    // placeholder from joining/creating a family (which already has an
    // `existingProfileId`, but was never something the user actually saved).
    // Only an established profile edited outside onboarding (e.g. from the
    // sidebar) should keep the user on this page with an in-place confirmation.
    const isFirstEverSave = Boolean(pending) || existingProfileId == null
    // Resend the originally persisted severity for any restriction the user
    // never touched (e.g. PREFERENCE, set via the family admin flow), instead
    // of the STRICT_AVOID/INTOLERANCE approximation `selected` displays it
    // as. Restrictions the user actually toggled use the new on/off value.
    const restrictionsToSave = Object.entries(selected).reduce<
      Record<number, ProfileRestrictionSeverity>
    >((accumulator, [restrictionId, severity]) => {
      const id = Number(restrictionId)
      const persistedSeverity = persistedSeverities[id]
      accumulator[id] =
        !touchedIds.has(id) && persistedSeverity
          ? (persistedSeverity as ProfileRestrictionSeverity)
          : severity
      return accumulator
    }, {})
    try {
      if (existingProfileId != null) {
        await selfProfileApiService.updateSelfProfile(normalizedProfileName, restrictionsToSave)
      } else {
        await selfProfileApiService.createSelfProfile(normalizedProfileName, restrictionsToSave)
      }
      if (isFirstEverSave) {
        pendingRegistrationOnboardingStore.clear()
        navigate(finishPath(pending?.invitationToken), {
          replace: true,
          state: { profileSetup: 'created' },
        })
      } else {
        setSuccessMessage('Your dietary profile has been saved successfully.')
      }
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page-shell">
      <section className="page-card" aria-labelledby="self-profile-setup-title">
        <p className="eyebrow">Dietary Profile Setup</p>
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
              setSuccessMessage('')
            }}
          />
          <p className="field-hint">This is the name for your personal dietary profile.</p>
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

        {successMessage ? (
          <p className="form-message form-message--success" role="status">
            {successMessage}
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

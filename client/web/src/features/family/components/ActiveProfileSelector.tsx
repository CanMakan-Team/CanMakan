import { useEffect, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import type { ActiveProfile, FamilyMember } from '../../../shared/api/types'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { familyApiService } from '../api/familyApiService'

/**
 * Active profile selector (UC11 demo surface on family members page).
 *
 * @author Amelia
 * @author YangMaowei
 */
export function ActiveProfileSelector({
  members,
}: {
  members: FamilyMember[]
}) {
  const selectable = members.filter((member) => member.profileActive)
  const [active, setActive] = useState<ActiveProfile | null>(null)
  const [switching, setSwitching] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  /** Load the active profile from the API. */
  useEffect(() => {
    void familyApiService.getActiveProfile().then(setActive).catch((caughtError) => {
      setError(getErrorMessage(caughtError))
    })
  }, [])

  /** Select a profile as the active profile. */
  const selectProfile = async (profileId: number) => {
    if (profileId === active?.profileId) return
    setSwitching(true)
    setMessage('')
    setError('')
    try {
      const selected = await familyApiService.setActiveProfile(profileId)
      setActive(selected)
      setMessage(`${selected.profileName} is now the active assessment profile.`)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSwitching(false)
    }
  }

  /** Render the active profile selector. */
  return (
    <section className="active-profile-card" aria-labelledby="active-profile-title">
      <div>
        <p className="eyebrow">Assessment context</p>
        <h2 id="active-profile-title">Active assessment profile</h2>
        <p>
          This single profile is used by assessment workflows. Inactive profiles
          cannot be selected.
        </p>
      </div>
      <div className="active-profile-card__control">
        <label htmlFor="active-profile-select">Selected profile</label>
        <select
          id="active-profile-select"
          value={active?.profileId ?? ''}
          disabled={switching || !active}
          onChange={(event) => void selectProfile(Number(event.target.value))}
        >
          {!active && <option value="">Loading…</option>}
          {selectable.map((member) => (
            <option key={member.profileId} value={member.profileId}>
              {member.profileName}
            </option>
          ))}
        </select>
        {active && <StatusBadge status="ACTIVE_PROFILE" />}
      </div>
      <div className="sr-live" aria-live="polite">
        {message}
        {error}
      </div>
    </section>
  )
}

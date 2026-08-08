import { useEffect, useState } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { familyService } from './familyService'
import type { ActiveProfile, FamilyMember } from '../../shared/api/types'
import { StatusBadge } from '../../shared/ui/StatusBadge'

/** Active profile selector
 * 
 * @author Amelia
 * @author YangMaowei
 */

export function ActiveProfileSelector({
  members,
}: {
  members: FamilyMember[]
}) {
  const [active, setActive] = useState<ActiveProfile | null>(null)
  const [switching, setSwitching] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  // This function is used to get the active profile from the server.
  // If the active profile is not found, it will set the error to the state.
  useEffect(() => {
    void familyService.getActiveProfile().then(setActive).catch((caughtError) => {
      setError(getErrorMessage(caughtError))
    })
  }, [])

  // This function is used to select the profile.
  // If the profile is already selected, it will return.
  // If the profile is not selected, it will set the switching state to true and set the message to the state.
  // If the profile is selected, it will set the active state to the selected profile and set the message to the state.
  // If the profile is not selected, it will set the error to the state.
  const selectProfile = async (memberId: number) => {
    if (memberId === active?.memberId) return
    setSwitching(true)
    setMessage('')
    setError('')
    try {
      const selected = await familyService.setActiveProfile(memberId)
      setActive(selected)
      setMessage(`${selected.profileName} is now the active assessment profile.`)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setSwitching(false)
    }
  }

  // Return the active profile selector
  return (
    <section className="active-profile-card" aria-labelledby="active-profile-title">
      <div>
        <p className="eyebrow">Assessment context</p>
        <h2 id="active-profile-title">Active assessment profile</h2>
        <p>
          This single profile is used by assessment workflows. “All profiles” is
          available only as a history filter.
        </p>
      </div>
      <div className="active-profile-card__control">
        <label htmlFor="active-profile-select">Selected profile</label>
        <select
          id="active-profile-select"
          value={active?.memberId ?? ''}
          disabled={switching || !active}
          onChange={(event) => void selectProfile(Number(event.target.value))}
        >
          {!active && <option value="">Loading…</option>}
          {members.map((member) => (
            <option key={member.memberId} value={member.memberId}>
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

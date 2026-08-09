import { useState, type FormEvent } from 'react'
import type {
  AgeGroup,
  FamilyProfileInput,
  Relationship,
  RestrictionCode,
} from '../../../shared/api/types'
import {
  ageGroupOptions,
  relationshipOptions,
  restrictionGroups,
} from '../lib/profileOptions'

const emptyProfile: FamilyProfileInput = {
  profileName: '',
  relationship: 'CHILD',
  ageGroup: 'CHILD',
  commonRequirements: [],
  restrictions: [],
}

export function ProfileForm({
  initialValue = emptyProfile,
  submitLabel,
  saving,
  error,
  onSubmit,
  onCancel,
}: {
  initialValue?: FamilyProfileInput
  submitLabel: string
  saving: boolean
  error: string
  onSubmit: (input: FamilyProfileInput) => Promise<void>
  onCancel: () => void
}) {
  const [form, setForm] = useState<FamilyProfileInput>(initialValue)
  const [nameError, setNameError] = useState('')

  const toggleRestriction = (
    code: RestrictionCode,
    target: 'commonRequirements' | 'restrictions',
  ) => {
    setForm((current) => ({
      ...current,
      [target]: current[target].includes(code)
        ? current[target].filter((item) => item !== code)
        : [...current[target], code],
    }))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const trimmedName = form.profileName.trim()
    if (!trimmedName) {
      setNameError('Enter a profile name.')
      return
    }
    setNameError('')
    await onSubmit({ ...form, profileName: trimmedName })
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="form-grid form-grid--two">
        <div className="field-group">
          <label htmlFor="profile-name">Profile name</label>
          <input
            id="profile-name"
            value={form.profileName}
            aria-invalid={Boolean(nameError)}
            aria-describedby={nameError ? 'profile-name-error' : undefined}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                profileName: event.target.value,
              }))
            }
          />
          {nameError && (
            <span id="profile-name-error" className="field-error">
              {nameError}
            </span>
          )}
        </div>
        <div className="field-group">
          <label htmlFor="relationship">Relationship</label>
          <select
            id="relationship"
            value={form.relationship}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                relationship: event.target.value as Relationship,
              }))
            }
          >
            {relationshipOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="age-group">Age group</label>
          <select
            id="age-group"
            value={form.ageGroup}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                ageGroup: event.target.value as AgeGroup,
              }))
            }
          >
            {ageGroupOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="restriction-picker">
        {restrictionGroups.map((group) => {
          const target =
            group.type === 'common' ? 'commonRequirements' : 'restrictions'
          return (
            <fieldset key={group.label}>
              <legend>
                {group.label}
                {group.type === 'common' && (
                  <span>Shared requirement where applicable</span>
                )}
              </legend>
              <div className="checkbox-grid">
                {group.options.map((option) => (
                  <label className="check-card" key={option.value}>
                    <input
                      type="checkbox"
                      checked={form[target].includes(option.value)}
                      onChange={() => toggleRestriction(option.value, target)}
                    />
                    <span>{option.label}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )
        })}
      </div>

      {error && (
        <p className="form-message form-message--error" role="alert">
          {error}
        </p>
      )}
      <div className="modal__actions">
        <button
          className="button button--secondary"
          type="button"
          onClick={onCancel}
          disabled={saving}
        >
          Cancel
        </button>
        <button className="button button--primary" type="submit" disabled={saving}>
          {saving ? 'Saving profile…' : submitLabel}
        </button>
      </div>
    </form>
  )
}

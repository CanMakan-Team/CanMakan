import { useEffect, useMemo, useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import type {
  FamilyProfileInput,
  Relationship,
  RestrictionCode,
} from '../../../shared/api/types'
import {
  groupCatalogByCategory,
  relationshipOptions,
  restrictionCategoryLabel,
} from '../lib/profileOptions'
import { getProfileNameError } from '../../../shared/validation/profileFields'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
} from '../../account/api/selfProfileApiService'
import { getErrorMessage } from '../../../shared/api/apiErrors'

/**
 * ProfileForm component for creating or editing a family dietary profile.
 *
 * @author Amelia
 * @author YangMaowei
 */

const RELIGIOUS_CATEGORY = 'RELIGIOUS'

const emptyProfile: FamilyProfileInput = {
  profileName: '',
  relationship: 'CHILD',
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
  allowRestrictionEdit = true,
  restrictionEditHint,
}: Readonly<{
  initialValue?: FamilyProfileInput
  submitLabel: string
  saving: boolean
  error: string
  onSubmit: (input: FamilyProfileInput) => Promise<void>
  onCancel: () => void
  /** When false, restriction checkboxes are read-only (D3). */
  allowRestrictionEdit?: boolean
  restrictionEditHint?: string
}>) {
  const [form, setForm] = useState<FamilyProfileInput>(initialValue)
  const [nameError, setNameError] = useState('')
  const [catalog, setCatalog] = useState<DietaryRestrictionOption[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState('')

  useEffect(() => {
    let active = true
    selfProfileApiService
      .getCatalog()
      .then((options) => {
        if (active) setCatalog(options)
      })
      .catch((caughtError: unknown) => {
        if (active) setCatalogError(getErrorMessage(caughtError))
      })
      .finally(() => {
        if (active) setCatalogLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const groupedCatalog = useMemo(
    () => groupCatalogByCategory(catalog),
    [catalog],
  )

  const toggleRestriction = (option: DietaryRestrictionOption) => {
    const code = option.code as RestrictionCode
    const target =
      option.category === RELIGIOUS_CATEGORY ? 'commonRequirements' : 'restrictions'
    setForm((current) => {
      const alreadySelected = current[target].includes(code)
      if (alreadySelected) {
        return {
          ...current,
          [target]: current[target].filter((item) => item !== code),
        }
      }
      if (option.category === RELIGIOUS_CATEGORY) {
        const religiousCodes = new Set(catalog
          .filter((item) => item.category === RELIGIOUS_CATEGORY)
          .map((item) => item.code as RestrictionCode))
        return {
          ...current,
          commonRequirements: [
            ...current.commonRequirements.filter((item) => !religiousCodes.has(item)),
            code,
          ],
        }
      }
      return {
        ...current,
        [target]: [...current[target], code],
      }
    })
  }

  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmedName = form.profileName.trim()
    const nextNameError = getProfileNameError(trimmedName)
    if (nextNameError) {
      setNameError(nextNameError)
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
            maxLength={100}
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
        {form.relationship !== 'SELF' && (
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
            {relationshipOptions
              .filter((option) => option.value !== 'SELF')
              .map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        )}
      </div>

      <div className="restriction-picker">
        {!allowRestrictionEdit && restrictionEditHint && (
          <output className="form-message">
            {restrictionEditHint}
          </output>
        )}
        {catalogLoading ? <output className="field-hint">Loading dietary options…</output> : null}
        {catalogError ? (
          <p className="form-message form-message--error" role="alert">
            {catalogError}
          </p>
        ) : null}
        {!catalogLoading
          ? groupedCatalog.map(([category, options]) => (
              <fieldset key={category} disabled={!allowRestrictionEdit}>
                <legend>{restrictionCategoryLabel(category)}</legend>
                <div className="checkbox-grid">
                  {options.map((option) => {
                    const code = option.code as RestrictionCode
                    const target =
                      option.category === RELIGIOUS_CATEGORY
                        ? 'commonRequirements'
                        : 'restrictions'
                    return (
                      <label className="check-card" key={option.id}>
                        <input
                          type="checkbox"
                          checked={form[target].includes(code)}
                          disabled={!allowRestrictionEdit}
                          onChange={() => toggleRestriction(option)}
                        />
                        <span>{option.displayName}</span>
                      </label>
                    )
                  })}
                </div>
              </fieldset>
            ))
          : null}
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
        <button className="button button--primary" type="submit" disabled={saving || catalogLoading}>
          {saving ? 'Saving profile…' : submitLabel}
        </button>
      </div>
    </form>
  )
}

import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'

type CreateFamilyCirclePageProps = {
  onCreated: () => void | Promise<void>
}

/**
 * UC8 explicit family-management action for creating a family circle.
 * 
 * @author Amelia
 */
export function CreateFamilyCirclePage({ onCreated }: CreateFamilyCirclePageProps) {
  const [familyName, setFamilyName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [validationError, setValidationError] = useState('')
  const [submitError, setSubmitError] = useState('')

  // Handle the submission of the form.
  // 1. Validate the form data and submit it to the server
  // 2. If the form data is valid, it will create a new family circle
  // 3. If the form data is invalid, it will set the validation error to the state
  // 5. If the form data is valid, it will set the submitting state to true
  const handleSubmit = async (event: ReactSubmitEvent<HTMLFormElement>) => {

    // Prevent the default form submission behavior
    event.preventDefault()
    
    // Reset the submit error
    setSubmitError('')

    // Validate the form data
    // 1. If the family name is empty, set the validation error to the state
    // 2. If the family name is more than 100 characters, set the validation error to the state
    // 3. If the family name is valid, continue to the next step
    const trimmed = familyName.trim()
    if (!trimmed) {
      setValidationError('Family name is required.')
      return
    }
    if (trimmed.length > 100) {
      setValidationError('Family name must be at most 100 characters.')
      return
    }

    // Here we are ready to submit the form data to the server
    // 1. Reset the validation error
    // 2. Set the submitting state to true
    // 3. Try to create a new family circle
    // 4. If the family circle is created successfully, call the onCreated callback
    //    (409 also reloads: caller already has a circle from a race/double-submit)
    // 5. If the family circle is not created successfully, set the submit error to the state
    // 6. Finally, set the submitting state to false
    setValidationError('')
    setSubmitting(true)
    try {
      await familyApiService.createFamily(trimmed)
      await onCreated()
    } catch (caughtError) {
      if (caughtError instanceof ApiError && caughtError.status === 409) {
        await onCreated()
        return
      }
      setSubmitError(getErrorMessage(caughtError))
    } finally {
      setSubmitting(false)
    }
  }

  // Return the create family circle page
  return (
    <section className="panel" aria-labelledby="create-family-heading">
      <header className="page-header">
        <div>
          <p className="eyebrow">Family Circle</p>
          <h1 id="create-family-heading">Create your family circle</h1>
          <p>
            Create a family circle if you want to manage or invite family members.
            Your personal Dietary Profile does not require a Family Circle.
          </p>
        </div>
      </header>

      <form
        className="stack-form stack-form--start-actions"
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
      >
        <label className="field">
          <span>Family name</span>
          <input
            type="text"
            name="familyName"
            maxLength={100}
            value={familyName}
            onChange={(event) => {
              setFamilyName(event.target.value)
              if (validationError) setValidationError('')
            }}
            disabled={submitting}
            aria-invalid={Boolean(validationError)}
            aria-describedby={validationError ? 'family-name-error' : undefined}
            autoComplete="organization"
            placeholder="e.g. Wong Family"
          />
        </label>
        {validationError ? (
          <p id="family-name-error" className="field-error" role="alert">
            {validationError}
          </p>
        ) : null}
        {submitError ? (
          <p className="field-error" role="alert">
            {submitError}
          </p>
        ) : null}
        <div className="page-header__actions">
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create family circle'}
          </button>
        </div>
      </form>
    </section>
  )
}

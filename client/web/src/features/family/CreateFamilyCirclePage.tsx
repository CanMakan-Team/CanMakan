import { useState, type SubmitEvent as ReactSubmitEvent } from 'react'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import { familyService } from './familyService'

type CreateFamilyCirclePageProps = {
  onCreated: () => void
}

/**
 * UC8 empty-state: create a family circle when GET /families/me is 404.
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
      setValidationError('Enter a family name to continue.')
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
    // 5. If the family circle is not created successfully, set the submit error to the state
    // 6. Finally, set the submitting state to false
    setValidationError('')
    setSubmitting(true)
    try {
      await familyService.createFamily(trimmed)
      onCreated()
    } catch (caughtError) {
      if (caughtError instanceof ApiError && caughtError.status === 409) {
        onCreated()
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
          <p className="eyebrow">Get started</p>
          <h1 id="create-family-heading">Create your family circle</h1>
          <p>
            You are not in a family circle yet. Choose a name to become the Family
            Admin and create your dietary profile.
          </p>
        </div>
      </header>

      <form className="stack-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
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

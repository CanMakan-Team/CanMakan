import { Link, Navigate, Outlet } from 'react-router-dom'
import { FAMILY_CIRCLE_PATH, ME_PATH } from '../../app/userPortalPaths'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { isPrimaryAdminRole } from './lib/familyRoles'
import { useFamilyMe } from './useFamilyMe'

/**
 * Uses the portal FamilyMe context so family-scoped pages do not fetch
 * GET /families/me again. Members are sent to /me. Missing membership offers
 * Family Circle options without opening create.
 */
export function FamilyMeGate() {
  const { family, loading, error, reload } = useFamilyMe()

  if (loading) {
    return <LoadingState label="Loading your family circle…" />
  }
  if (error) {
    return <ErrorState message={error} onRetry={reload} />
  }
  if (!family) {
    return (
      <section className="panel" aria-labelledby="family-required-heading">
        <p className="eyebrow">Optional Family Circle</p>
        <h1 id="family-required-heading">This feature uses a Family Circle</h1>
        <p>
          Your personal account remains available without one. Household
          management is for the family admin after a circle exists.
        </p>
        <div className="page-header__actions">
          <Link className="button button--primary" to={FAMILY_CIRCLE_PATH}>
            Open Family Circle options
          </Link>
          <Link className="button button--secondary" to={ME_PATH}>
            Return to personal home
          </Link>
        </div>
      </section>
    )
  }

  if (!isPrimaryAdminRole(family.memberRole)) {
    return <Navigate to={ME_PATH} replace />
  }

  return <Outlet />
}

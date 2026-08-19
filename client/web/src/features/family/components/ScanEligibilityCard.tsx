import { Link } from 'react-router-dom'
import { FAMILY_RESTRICTIONS_PATH } from '../../../app/userPortalPaths'
import type { FamilyMember } from '../../../shared/api/types'

/**
 * Roster snapshot of who can be selected for mobile scans (UC12).
 * Profile switching for assessments stays on mobile (UC11).
 *
 * @author Amelia
 */
export function ScanEligibilityCard({
  members,
}: Readonly<{
  members: FamilyMember[]
}>) {
  const eligibleCount = members.filter((member) => member.profileActive).length
  const inactiveCount = members.length - eligibleCount

  return (
    <section className="roster-context-card" aria-labelledby="scan-eligibility-title">
      <div>
        <p className="eyebrow">Scan eligibility</p>
        <h2 id="scan-eligibility-title" className="roster-context-card__title">
          Who can be scanned for
        </h2>
        <p>
          Inactive profiles stay on this roster but cannot be selected for scans.
          Choose whose profile a scan uses in the CanMakan mobile app.
        </p>
      </div>
      <div className="roster-context-card__aside">
        <div className="roster-context-card__stats">
          <div className="roster-context-card__stat">
            <span>Eligible</span>
            <strong>{eligibleCount}</strong>
          </div>
          <div className="roster-context-card__stat">
            <span>Inactive</span>
            <strong>{inactiveCount}</strong>
          </div>
        </div>
        <Link className="roster-context-card__link" to={FAMILY_RESTRICTIONS_PATH}>
          Review restriction summary
        </Link>
      </div>
    </section>
  )
}

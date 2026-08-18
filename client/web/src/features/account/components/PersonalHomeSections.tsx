import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import {
  FAMILY_CIRCLE_PATH,
  FAMILY_DASHBOARD_PATH,
  ME_ACCOUNT_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'
import { formatCode } from '../../family/lib/profileOptions'
import type { AuthenticatedSession, FamilyMe } from '../../../shared/api/types'
import type { PersonalScanHistoryItem, SelfProfileResponse } from '../api/selfProfileApiService'
import { ErrorState } from '../../../shared/ui/PageState'
import { CanMakanMascot } from '../../../shared/ui/CanMakanMascot'
import { PortalIcon } from '../../../shared/ui/PortalIcon'

export function PersonalHomeBanners({
  showMobilePromo,
  testerNoticeDismissed,
  firebaseAppDistributionUrl,
  onDismissInstalled,
  onDismissTesterNotice,
}: {
  showMobilePromo: boolean
  testerNoticeDismissed: boolean
  firebaseAppDistributionUrl: string
  onDismissInstalled: () => void
  onDismissTesterNotice: () => void
}) {
  return (
    <>
      {showMobilePromo ? (
        <aside className="home-banner" aria-labelledby="mobile-app-heading">
          <div className="home-banner__copy">
            <p className="eyebrow">Mobile app</p>
            <h2 id="mobile-app-heading">Get CanMakan on Mobile</h2>
            <p>
              Scan ingredient lists on the go and check dietary safety instantly.
            </p>
            <button
              type="button"
              className="button button--secondary home-banner__installed"
              onClick={onDismissInstalled}
            >
              I already have it installed
            </button>
          </div>
          <a
            className="home-banner__qr"
            href={firebaseAppDistributionUrl}
            target="_blank"
            rel="noreferrer"
            aria-label="QR code for Firebase App Distribution"
          >
            <QRCodeSVG value={firebaseAppDistributionUrl} size={116} />
          </a>
        </aside>
      ) : null}

      {showMobilePromo && !testerNoticeDismissed ? (
        <div className="notice notice--neutral home-tester-notice" role="status">
          <p>
            Tester build: the Android app is on Firebase App Distribution, not
            the App Store or Google Play.
          </p>
          <button
            type="button"
            className="home-tester-notice__dismiss"
            onClick={onDismissTesterNotice}
          >
            Dismiss
          </button>
        </div>
      ) : null}
    </>
  )
}

export function PersonalHomeSummary({
  session,
  profile,
  profileLoading,
  profileError,
  restrictionNames,
  hasFamily,
  familyLoading,
  isPrimaryAdmin,
  family,
  memberCount,
  onRetryProfile,
}: {
  session: AuthenticatedSession | null
  profile: SelfProfileResponse | null
  profileLoading: boolean
  profileError: string
  restrictionNames: string[]
  hasFamily: boolean
  familyLoading: boolean
  isPrimaryAdmin: boolean
  family: FamilyMe | null | undefined
  memberCount: number | null
  onRetryProfile: () => void
}) {
  return (
    <section className="summary-grid summary-grid--home" aria-label="Account summary">
      <article className="summary-card home-card">
        <span className="summary-card__icon" aria-hidden="true">
          <PortalIcon name="gear" />
        </span>
        <div>
          <span>Account</span>
          <strong className="home-card__email">{session?.email ?? '—'}</strong>
          <span
            className={`status-badge ${session?.active ? 'status-badge--active' : 'status-badge--suspended'}`}
          >
            {session?.active ? 'Active' : 'Unavailable'}
          </span>
          <Link className="button button--secondary home-card__cta" to={ME_ACCOUNT_PATH}>
            Manage settings
          </Link>
        </div>
      </article>

      <article className={`summary-card home-card${profile || profileError ? '' : ' home-card--action'}`}>
        <span className="summary-card__icon" aria-hidden="true">
          <PortalIcon name="person" />
        </span>
        <div>
          <span>Dietary Profile</span>
          {profileLoading ? (
            <strong>Checking…</strong>
          ) : profileError ? (
            <ErrorState message={profileError} onRetry={onRetryProfile} />
          ) : profile ? (
            <>
              <strong>
                {restrictionNames.length === 0
                  ? `${profile.profileName} is ready`
                  : `${restrictionNames.length} active restriction${restrictionNames.length === 1 ? '' : 's'}`}
              </strong>
              <small>
                {restrictionNames.length
                  ? restrictionNames.slice(0, 3).join(', ')
                  : 'No restrictions recorded yet. Add them so app scans can warn you.'}
              </small>
              <Link className="button button--secondary home-card__cta" to={ME_SETUP_PROFILE_PATH}>
                Edit profile
              </Link>
            </>
          ) : (
            <>
              <strong>Not set up yet</strong>
              <small>
                Configure restrictions like Halal, dairy, or gluten so app scans
                can match your needs.
              </small>
              <Link className="button button--primary home-card__cta" to={ME_SETUP_PROFILE_PATH}>
                Set Up Profile
              </Link>
            </>
          )}
        </div>
      </article>

      <article className={`summary-card home-card${hasFamily ? '' : ' home-card--action'}`}>
        <span className="summary-card__icon" aria-hidden="true">
          <PortalIcon name="people" />
        </span>
        <div>
          <span>Family Circle</span>
          {familyLoading ? (
            <strong>Checking…</strong>
          ) : hasFamily ? (
            <>
              <strong>
                {memberCount != null
                  ? `${memberCount} household member${memberCount === 1 ? '' : 's'}`
                  : family?.familyName ?? 'Your household'}
              </strong>
              <small>
                {isPrimaryAdmin
                  ? `${family?.familyName ?? 'Your circle'} — manage profiles on the web.`
                  : `${family?.familyName ?? 'Your circle'} — household tools are for the family admin. Scan in the app.`}
              </small>
              {isPrimaryAdmin ? (
                <Link className="button button--secondary home-card__cta" to={FAMILY_DASHBOARD_PATH}>
                  View circle
                </Link>
              ) : (
                <span className="home-card__cta home-card__hint">Scan in the mobile app</span>
              )}
            </>
          ) : (
            <>
              <strong>Manage household dietary needs in one place</strong>
              <small>
                Optional. Create a circle only if you want shared profiles for
                your household.
              </small>
              <Link className="button button--primary home-card__cta" to={FAMILY_CIRCLE_PATH}>
                Create Circle
              </Link>
            </>
          )}
        </div>
      </article>
    </section>
  )
}

export function PersonalHomeSetup({
  setupFinished,
  setupCompleteCount,
  profile,
  profileError,
  hasFamily,
}: {
  setupFinished: boolean
  setupCompleteCount: number
  profile: SelfProfileResponse | null
  profileError: string
  hasFamily: boolean
}) {
  if (setupFinished) {
    return (
      <p className="setup-complete-banner" role="status">
        <span aria-hidden="true">✓</span>
        All setup steps completed
      </p>
    )
  }

  return (
    <section className="panel" aria-labelledby="setup-progress-title">
      <p className="eyebrow">Setup</p>
      <h2 id="setup-progress-title">Account setup: {setupCompleteCount}/3 complete</h2>
      <p>Finish these once so scans on your phone have the right dietary context.</p>
      <ol className="setup-steps">
        <li className="setup-steps__item setup-steps__item--done">
          <span aria-hidden="true">✓</span>
          Account created
        </li>
        <li className={`setup-steps__item${profile ? ' setup-steps__item--done' : ''}`}>
          <span aria-hidden="true">{profile ? '✓' : '○'}</span>
          {profile || profileError ? (
            'Dietary Profile'
          ) : (
            <Link to={ME_SETUP_PROFILE_PATH}>Dietary Profile</Link>
          )}
        </li>
        <li className={`setup-steps__item${hasFamily ? ' setup-steps__item--done' : ''}`}>
          <span aria-hidden="true">{hasFamily ? '✓' : '○'}</span>
          {hasFamily ? (
            'Family Circle'
          ) : (
            <Link to={FAMILY_CIRCLE_PATH}>Family Circle</Link>
          )}
        </li>
      </ol>
    </section>
  )
}

export function PersonalHomeRecentScans({
  profile,
  recentScans,
}: {
  profile: SelfProfileResponse | null
  recentScans: PersonalScanHistoryItem[]
}) {
  return (
    <section className="panel home-recent-panel" aria-labelledby="recent-activity-title">
      <p className="eyebrow">Activity</p>
      <h2 id="recent-activity-title">Recent scans</h2>
      {profile && recentScans.length > 0 ? (
        <div className="recent-list home-scan-list">
          {recentScans.map((scan) => {
            const productName = scan.product?.productName || 'Scanned product'
            const brand = scan.product?.brand?.trim() || ''
            const summary = scan.aiExplanation?.trim() || ''
            const verdictKey = scan.verdict?.trim().toLowerCase() || 'unknown'
            return (
              <article
                key={scan.id}
                className="home-scan-row"
                tabIndex={summary ? 0 : undefined}
              >
                <span className="home-scan-row__thumb" aria-hidden="true">
                  {productName.slice(0, 1).toUpperCase()}
                </span>
                <div>
                  <strong>{productName}</strong>
                  <span>
                    {brand || 'Unknown brand'}
                    {' · '}
                    {formatScanTime(scan.scannedAt)}
                  </span>
                </div>
                <span className={`status-badge status-badge--${verdictKey}`}>
                  {formatCode(scan.verdict || 'UNKNOWN')}
                </span>
                {summary ? (
                  <p className="home-scan-row__tip" role="tooltip">
                    {summary}
                  </p>
                ) : null}
              </article>
            )
          })}
        </div>
      ) : (
        <div className="home-empty-scans">
          <CanMakanMascot pose="scan" size="banner" alt="" />
          <p>
            No web scan history yet. Open CanMakan on your phone to check a
            packaged food against your profile.
          </p>
        </div>
      )}
    </section>
  )
}

function formatScanTime(iso: string): string {
  const parsed = Date.parse(iso)
  if (Number.isNaN(parsed)) return iso
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(parsed)
}

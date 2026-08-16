import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  FAMILY_CIRCLE_PATH,
  FAMILY_DASHBOARD_PATH,
  ME_ACCOUNT_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'
import { ApiError } from '../../../shared/api/apiErrors'
import { formatCode } from '../../family/lib/profileOptions'
import { familyApiService } from '../../family/api/familyApiService'
import { useFamilyMe } from '../../family/useFamilyMe'
import { useSession } from '../../auth/useSession'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
  type PersonalScanHistoryItem,
  type SelfProfileResponse,
} from '../api/selfProfileApiService'
import { getFirebaseAppDistributionUrl } from '../lib/firebaseAppDistribution'
import { QRCodeSVG } from 'qrcode.react'
import { CanMakanMascot } from '../../../shared/ui/CanMakanMascot'

type PersonalHomeState = {
  profileSetup?: 'created' | 'deferred'
}

const RECENT_SCAN_LIMIT = 3
const TESTER_NOTICE_STORAGE_KEY = 'canmakan.home.tester-distribution-notice.dismissed'

/** USER desk: account, dietary profile, optional Family Circle, and mobile scanning. */
export function PersonalHomePage() {
  const { session } = useSession()
  const { family, hasFamily, isPrimaryAdmin, loading: familyLoading } = useFamilyMe()
  const location = useLocation()
  const navigationState = location.state as PersonalHomeState | null
  const firebaseAppDistributionUrl = getFirebaseAppDistributionUrl()

  const [profile, setProfile] = useState<SelfProfileResponse | null>(null)
  const [catalog, setCatalog] = useState<DietaryRestrictionOption[]>([])
  const [profileLoading, setProfileLoading] = useState(true)
  const [memberCount, setMemberCount] = useState<number | null>(null)
  const [recentScans, setRecentScans] = useState<PersonalScanHistoryItem[]>([])
  const [testerNoticeDismissed, setTesterNoticeDismissed] = useState(() =>
    readTesterNoticeDismissed(),
  )

  useEffect(() => {
    let active = true
    Promise.all([
      selfProfileApiService.getCatalog().catch(() => [] as DietaryRestrictionOption[]),
      selfProfileApiService.getSelfProfile().catch((caught: unknown) => {
        if (caught instanceof ApiError && caught.status === 404) return null
        return null
      }),
    ]).then(([options, existing]) => {
      if (!active) return
      setCatalog(options)
      setProfile(existing)
      setProfileLoading(false)
    })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (familyLoading || !hasFamily) {
      return
    }
    let active = true
    familyApiService.getMembers().then(
      (members) => {
        if (active) setMemberCount(members.length)
      },
      () => undefined,
    )
    return () => {
      active = false
    }
  }, [familyLoading, hasFamily])

  useEffect(() => {
    const profileId = profile?.profileId
    if (profileId == null) {
      return
    }
    let active = true
    selfProfileApiService.getScanHistoryForProfile(profileId).then(
      (scans) => {
        if (active) setRecentScans(scans.slice(0, RECENT_SCAN_LIMIT))
      },
      () => undefined,
    )
    return () => {
      active = false
    }
  }, [profile?.profileId])

  const restrictionNames = useMemo(
    () => (profile ? labelsForRestrictions(profile.restrictions, catalog) : []),
    [profile, catalog],
  )

  const setupCompleteCount =
    1 + (profile ? 1 : 0) + (hasFamily ? 1 : 0)

  const notice =
    navigationState?.profileSetup === 'created'
      ? 'Your personal Dietary Profile was created successfully.'
      : navigationState?.profileSetup === 'deferred'
        ? 'Dietary Profile setup was skipped. You can set it up whenever you are ready.'
        : ''

  return (
    <div className="personal-home">
      <header className="page-header">
        <div>
          <p className="eyebrow">User Portal</p>
          <h1>Your CanMakan account</h1>
          <p>
            Keep your dietary needs ready for scans. Grocery checks happen in the
            CanMakan mobile app
            {isPrimaryAdmin ? '; household tools stay on the web.' : '.'}
          </p>
        </div>
      </header>

      {notice ? (
        <p className="notice notice--neutral" role="status">
          {notice}
        </p>
      ) : null}

      <aside className="home-banner" aria-labelledby="mobile-app-heading">
        <div className="home-banner__copy">
          <p className="eyebrow">Mobile app</p>
          <h2 id="mobile-app-heading">Get CanMakan on Mobile</h2>
          <p>
            Scan ingredient lists on the go and check dietary safety instantly.
          </p>
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

      {testerNoticeDismissed ? null : (
        <div className="notice notice--neutral home-tester-notice" role="status">
          <p>
            Tester build: the Android app is on Firebase App Distribution, not
            the App Store or Google Play.
          </p>
          <button
            type="button"
            className="home-tester-notice__dismiss"
            onClick={() => {
              writeTesterNoticeDismissed()
              setTesterNoticeDismissed(true)
            }}
          >
            Dismiss
          </button>
        </div>
      )}

      <section className="summary-grid summary-grid--home" aria-label="Account summary">
        <article className="summary-card home-card">
          <span className="summary-card__icon" aria-hidden="true">
            @
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

        <article className={`summary-card home-card${profile ? '' : ' home-card--action'}`}>
          <span className="summary-card__icon" aria-hidden="true">
            ◇
          </span>
          <div>
            <span>Dietary Profile</span>
            {profileLoading ? (
              <strong>Checking…</strong>
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
            ♙
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

      <section className="panel" aria-labelledby="setup-progress-title">
        <p className="eyebrow">Setup</p>
        <h2 id="setup-progress-title">Account setup: {setupCompleteCount}/3 complete</h2>
        <p>Finish these once so scans on your phone have the right dietary context.</p>
        <ol className="setup-steps">
          <li className="setup-steps__item setup-steps__item--done">
            <span aria-hidden="true">✓</span>
            Account created
          </li>
          <li
            className={`setup-steps__item${profile ? ' setup-steps__item--done' : ''}`}
          >
            <span aria-hidden="true">{profile ? '✓' : '○'}</span>
            {profile ? (
              'Dietary Profile'
            ) : (
              <Link to={ME_SETUP_PROFILE_PATH}>Dietary Profile</Link>
            )}
          </li>
          <li
            className={`setup-steps__item${hasFamily ? ' setup-steps__item--done' : ''}`}
          >
            <span aria-hidden="true">{hasFamily ? '✓' : '○'}</span>
            {hasFamily ? (
              'Family Circle'
            ) : (
              <Link to={FAMILY_CIRCLE_PATH}>Family Circle</Link>
            )}
          </li>
        </ol>
      </section>

      <section className="panel" aria-labelledby="recent-activity-title">
        <p className="eyebrow">Activity</p>
        <h2 id="recent-activity-title">Recent scans</h2>
        {profile && recentScans.length > 0 ? (
          <div className="recent-list">
            {recentScans.map((scan) => (
              <article key={scan.id}>
                <div>
                  <strong>{scan.product?.productName || 'Scanned product'}</strong>
                  <span>{formatScanTime(scan.scannedAt)}</span>
                </div>
                <span className={`status-badge status-badge--${scan.verdict.toLowerCase()}`}>
                  {formatCode(scan.verdict)}
                </span>
              </article>
            ))}
          </div>
        ) : (
          <div className="home-empty-scans">
            <CanMakanMascot pose="scan" size="banner" alt="" />
            <div>
              <p>
                No web scan history yet. Open CanMakan on your phone to check a
                packaged food against your profile.
              </p>
              <article className="home-empty-scans__sample" aria-label="Example scan result">
                <div>
                  <p className="eyebrow">Example</p>
                  <strong>Packaged snack</strong>
                  <span>How a phone scan will appear here</span>
                </div>
                <span className="status-badge status-badge--safe">Safe</span>
              </article>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}

function labelsForRestrictions(
  restrictions: Record<string, string>,
  catalog: DietaryRestrictionOption[],
): string[] {
  const byId = new Map(catalog.map((option) => [String(option.id), option.displayName]))
  const byCode = new Map(
    catalog.map((option) => [option.code.toUpperCase(), option.displayName]),
  )
  return Object.keys(restrictions).map(
    (key) => byId.get(key) ?? byCode.get(key.toUpperCase()) ?? formatCode(key),
  )
}

function readTesterNoticeDismissed(): boolean {
  try {
    return localStorage.getItem(TESTER_NOTICE_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function writeTesterNoticeDismissed() {
  try {
    localStorage.setItem(TESTER_NOTICE_STORAGE_KEY, '1')
  } catch {
    return
  }
}

function formatScanTime(iso: string): string {
  const parsed = Date.parse(iso)
  if (Number.isNaN(parsed)) return iso
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(parsed)
}

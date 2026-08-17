import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  FAMILY_CIRCLE_PATH,
  FAMILY_DASHBOARD_PATH,
  ME_ACCOUNT_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
import { formatCode } from '../../family/lib/profileOptions'
import { getGreetingPeriod } from '../../family/lib/greeting'
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
import { ErrorState } from '../../../shared/ui/PageState'
import { CanMakanMascot } from '../../../shared/ui/CanMakanMascot'
import { PortalIcon } from '../../../shared/ui/PortalIcon'

type PersonalHomeState = {
  profileSetup?: 'created'
}

const RECENT_SCAN_LIMIT = 10
const TESTER_NOTICE_STORAGE_KEY = 'canmakan.home.tester-distribution-notice.dismissed'
const MOBILE_APP_INSTALLED_STORAGE_KEY = 'canmakan.home.mobile-app-installed'

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
  const [profileError, setProfileError] = useState('')
  const [profileLoadAttempt, setProfileLoadAttempt] = useState(0)
  const [memberCount, setMemberCount] = useState<number | null>(null)
  const [fetchedScans, setFetchedScans] = useState<PersonalScanHistoryItem[]>([])
  const [loadedScanProfileId, setLoadedScanProfileId] = useState<number | null>(null)
  const [testerNoticeDismissed, setTesterNoticeDismissed] = useState(() =>
    readTesterNoticeDismissed(),
  )
  const sessionUserId = session?.userId
  const [dismissedInstalledForUserId, setDismissedInstalledForUserId] = useState<
    number | undefined
  >()
  const appInstalledDismissed =
    (sessionUserId != null && dismissedInstalledForUserId === sessionUserId) ||
    readMobileAppInstalled(sessionUserId)

  useEffect(() => {
    let active = true
    Promise.all([
      selfProfileApiService.getCatalog().catch(() => [] as DietaryRestrictionOption[]),
      selfProfileApiService.getSelfProfile().then(
        (existing) => existing,
        (caught: unknown) => {
          if (caught instanceof ApiError && caught.status === 404) return null
          throw caught
        },
      ),
    ]).then(
      ([options, existing]) => {
        if (!active) return
        setCatalog(options)
        setProfile(existing)
        setProfileError('')
        setProfileLoading(false)
      },
      (caught: unknown) => {
        if (!active) return
        setProfile(null)
        setProfileError(getErrorMessage(caught))
        setProfileLoading(false)
      },
    )
    return () => {
      active = false
    }
  }, [profileLoadAttempt])

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

  const profileId = profile?.profileId
  useEffect(() => {
    if (profileLoading || profileId == null) {
      return
    }
    let active = true
    selfProfileApiService.getScanHistoryForProfile(profileId).then(
      (scans) => {
        if (!active) return
        setFetchedScans(scans.slice(0, RECENT_SCAN_LIMIT))
        setLoadedScanProfileId(profileId)
      },
      () => {
        if (!active) return
        setFetchedScans([])
        setLoadedScanProfileId(profileId)
      },
    )
    return () => {
      active = false
    }
  }, [profileLoading, profileId])

  const recentScans =
    profileId == null || loadedScanProfileId !== profileId ? [] : fetchedScans
  const scanHistoryReady =
    !profileLoading &&
    !profileError &&
    (profileId == null || loadedScanProfileId === profileId)

  const restrictionNames = useMemo(
    () => (profile ? labelsForRestrictions(profile.restrictions, catalog) : []),
    [profile, catalog],
  )

  const setupCompleteCount =
    1 + (profile ? 1 : 0) + (hasFamily ? 1 : 0)
  const setupFinished = setupCompleteCount === 3

  const notice =
    navigationState?.profileSetup === 'created'
      ? 'Your personal Dietary Profile was created successfully.'
      : ''

  const showMobilePromo =
    scanHistoryReady && recentScans.length === 0 && !appInstalledDismissed

  // The signed-in display name is used so the greeting is available immediately,
  // before the dietary profile request resolves.
  const greetingPeriod = getGreetingPeriod()
  const greetingName = session?.displayName ?? 'there'

  return (
    <div className="personal-home">
      <header className="page-header">
        <div>
          <p className="eyebrow">User Portal</p>
          <h1>
            Good {greetingPeriod}, {greetingName}.
          </h1>
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
              onClick={() => {
                writeMobileAppInstalled(sessionUserId)
                setDismissedInstalledForUserId(sessionUserId)
              }}
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
            onClick={() => {
              writeTesterNoticeDismissed()
              setTesterNoticeDismissed(true)
            }}
          >
            Dismiss
          </button>
        </div>
      ) : null}

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
              <ErrorState
                message={profileError}
                onRetry={() => {
                  setProfileLoading(true)
                  setProfileError('')
                  setProfileLoadAttempt((current) => current + 1)
                }}
              />
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

      {setupFinished ? (
        <p className="setup-complete-banner" role="status">
          <span aria-hidden="true">✓</span>
          All setup steps completed
        </p>
      ) : (
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
            ) : profileError ? (
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
      )}

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

function mobileAppInstalledStorageKey(userId?: number) {
  return userId == null
    ? MOBILE_APP_INSTALLED_STORAGE_KEY
    : `${MOBILE_APP_INSTALLED_STORAGE_KEY}.${userId}`
}

function readMobileAppInstalled(userId?: number): boolean {
  try {
    return localStorage.getItem(mobileAppInstalledStorageKey(userId)) === '1'
  } catch {
    return false
  }
}

function writeMobileAppInstalled(userId?: number) {
  try {
    localStorage.setItem(mobileAppInstalledStorageKey(userId), '1')
  } catch {
    return
  }
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

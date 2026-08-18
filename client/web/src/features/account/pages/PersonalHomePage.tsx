import { getGreetingPeriod } from '../../family/lib/greeting'
import { usePersonalHomeData } from '../hooks/usePersonalHomeData'
import {
  PersonalHomeBanners,
  PersonalHomeRecentScans,
  PersonalHomeSetup,
  PersonalHomeSummary,
} from '../components/PersonalHomeSections'

/** USER desk: account, dietary profile, optional Family Circle, and mobile scanning. */
export function PersonalHomePage() {
  const home = usePersonalHomeData()
  const greetingPeriod = getGreetingPeriod()
  const greetingName = home.session?.displayName ?? 'there'

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
            {home.isPrimaryAdmin ? '; household tools stay on the web.' : '.'}
          </p>
        </div>
      </header>

      {home.notice ? (
        <p className="notice notice--neutral" role="status">
          {home.notice}
        </p>
      ) : null}

      <PersonalHomeBanners
        showMobilePromo={home.showMobilePromo}
        testerNoticeDismissed={home.testerNoticeDismissed}
        firebaseAppDistributionUrl={home.firebaseAppDistributionUrl}
        onDismissInstalled={home.dismissInstalled}
        onDismissTesterNotice={home.dismissTesterNotice}
      />

      <PersonalHomeSummary
        session={home.session}
        profile={home.profile}
        profileLoading={home.profileLoading}
        profileError={home.profileError}
        restrictionNames={home.restrictionNames}
        hasFamily={home.hasFamily}
        familyLoading={home.familyLoading}
        isPrimaryAdmin={home.isPrimaryAdmin}
        family={home.family}
        memberCount={home.memberCount}
        onRetryProfile={home.retryProfileLoad}
      />

      <PersonalHomeSetup
        setupFinished={home.setupFinished}
        setupCompleteCount={home.setupCompleteCount}
        profile={home.profile}
        profileError={home.profileError}
        hasFamily={home.hasFamily}
      />

      <PersonalHomeRecentScans profile={home.profile} recentScans={home.recentScans} />
    </div>
  )
}

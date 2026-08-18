import { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
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
import {
  readMobileAppInstalled,
  readTesterNoticeDismissed,
  writeMobileAppInstalled,
  writeTesterNoticeDismissed,
} from '../lib/personalHomeStorage'

type PersonalHomeState = {
  profileSetup?: 'created'
}

const RECENT_SCAN_LIMIT = 10

export function usePersonalHomeData() {
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

  const setupCompleteCount = 1 + (profile ? 1 : 0) + (hasFamily ? 1 : 0)
  const setupFinished = setupCompleteCount === 3
  const notice =
    navigationState?.profileSetup === 'created'
      ? 'Your personal Dietary Profile was created successfully.'
      : ''
  const showMobilePromo =
    scanHistoryReady && recentScans.length === 0 && !appInstalledDismissed

  const retryProfileLoad = () => {
    setProfileLoading(true)
    setProfileError('')
    setProfileLoadAttempt((current) => current + 1)
  }

  const dismissInstalled = () => {
    writeMobileAppInstalled(sessionUserId)
    setDismissedInstalledForUserId(sessionUserId)
  }

  const dismissTesterNotice = () => {
    writeTesterNoticeDismissed()
    setTesterNoticeDismissed(true)
  }

  return {
    session,
    family,
    hasFamily,
    isPrimaryAdmin,
    familyLoading,
    firebaseAppDistributionUrl,
    profile,
    profileLoading,
    profileError,
    memberCount,
    recentScans,
    restrictionNames,
    setupCompleteCount,
    setupFinished,
    notice,
    showMobilePromo,
    testerNoticeDismissed,
    retryProfileLoad,
    dismissInstalled,
    dismissTesterNotice,
  }
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

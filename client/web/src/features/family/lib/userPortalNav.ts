import {
  FAMILY_CIRCLE_PATH,
  FAMILY_DASHBOARD_PATH,
  FAMILY_HISTORY_PATH,
  FAMILY_MEMBERS_PATH,
  FAMILY_RESTRICTIONS_PATH,
  FAMILY_VERDICT_TRENDS_PATH,
  ME_ACCOUNT_PATH,
  ME_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'

export interface NavigationItem {
  label: string
  to: string
  icon: string
}

export interface NavigationSection {
  label: string
  items: NavigationItem[]
}

/** Sidebar sections for the USER portal, based on Family Circle membership. */
export function userPortalSections(options: {
  hasFamily: boolean
  isPrimaryAdmin: boolean
  loading?: boolean
}): NavigationSection[] {
  const personal: NavigationSection = {
    label: 'Personal',
    items: [
      { label: 'Home', to: ME_PATH, icon: 'home' },
      { label: 'Dietary Profile', to: ME_SETUP_PROFILE_PATH, icon: 'profile' },
      { label: 'Account Settings', to: ME_ACCOUNT_PATH, icon: 'gear' },
    ],
  }

  const familyAdmin: NavigationSection = {
    label: 'Family',
    items: [
      { label: 'Family Overview', to: FAMILY_DASHBOARD_PATH, icon: 'overview' },
      { label: 'Family Members', to: FAMILY_MEMBERS_PATH, icon: 'people' },
      { label: 'Restriction Summary', to: FAMILY_RESTRICTIONS_PATH, icon: 'restrictions' },
      { label: 'Family Scan History', to: FAMILY_HISTORY_PATH, icon: 'history' },
      { label: 'Verdict Trends', to: FAMILY_VERDICT_TRENDS_PATH, icon: 'trends' },
    ],
  }

  if (options.isPrimaryAdmin) {
    return [personal, familyAdmin]
  }

  if (options.loading) {
    return [personal]
  }

  if (!options.hasFamily) {
    return [
      personal,
      {
        label: 'Family',
        items: [{ label: 'Create Family Circle', to: FAMILY_CIRCLE_PATH, icon: 'addPeople' }],
      },
    ]
  }

  return [personal]
}

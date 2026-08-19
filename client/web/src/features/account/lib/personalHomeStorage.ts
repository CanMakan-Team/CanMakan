const TESTER_NOTICE_STORAGE_KEY = 'canmakan.home.tester-distribution-notice.dismissed'
const MOBILE_APP_INSTALLED_STORAGE_KEY = 'canmakan.home.mobile-app-installed'

function mobileAppInstalledStorageKey(userId?: number) {
  return userId == null
    ? MOBILE_APP_INSTALLED_STORAGE_KEY
    : `${MOBILE_APP_INSTALLED_STORAGE_KEY}.${userId}`
}

export function readMobileAppInstalled(userId?: number): boolean {
  try {
    return localStorage.getItem(mobileAppInstalledStorageKey(userId)) === '1'
  } catch {
    return false
  }
}

export function writeMobileAppInstalled(userId?: number) {
  try {
    localStorage.setItem(mobileAppInstalledStorageKey(userId), '1')
  } catch {
    return
  }
}

export function readTesterNoticeDismissed(): boolean {
  try {
    return localStorage.getItem(TESTER_NOTICE_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function writeTesterNoticeDismissed() {
  try {
    localStorage.setItem(TESTER_NOTICE_STORAGE_KEY, '1')
  } catch {
    return
  }
}

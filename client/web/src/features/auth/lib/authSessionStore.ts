import type { AuthenticatedSession } from '../../../shared/api/types'

const legacySessionKey = 'canmakan.session'
const accountBoundMockKey = 'canmakan.mock.family'
const sessionEventKey = 'canmakan.session-event'
const sessionChannelName = 'canmakan.session-events'

type SessionEventType = 'logout' | 'account-change'
interface SessionEvent {
  type: SessionEventType
  generation: number
  timestamp: number
  coordinationId: string
}

type SessionListener = (session: AuthenticatedSession | null) => void

let currentSession: AuthenticatedSession | null = null
let generation = 0
const listeners = new Set<SessionListener>()
const sessionChannel =
  typeof BroadcastChannel === 'undefined'
    ? null
    : new BroadcastChannel(sessionChannelName)

function clearAccountBoundBrowserState() {
  localStorage.removeItem(legacySessionKey)
  localStorage.removeItem(accountBoundMockKey)
}

function publish() {
  for (const listener of listeners) listener(currentSession)
}

function clearLocalSession() {
  currentSession = null
  generation += 1
  clearAccountBoundBrowserState()
  publish()
}

function isSessionEvent(value: unknown): value is SessionEvent {
  if (!value || typeof value !== 'object') return false
  const event = value as Partial<SessionEvent>
  return (
    (event.type === 'logout' || event.type === 'account-change') &&
    Number.isSafeInteger(event.generation) &&
    typeof event.timestamp === 'number' &&
    typeof event.coordinationId === 'string' &&
    event.coordinationId.length > 0
  )
}

function handleExternalEvent(value: unknown) {
  if (isSessionEvent(value)) clearLocalSession()
}

function createSessionEvent(type: SessionEventType): SessionEvent {
  return {
    type,
    generation,
    timestamp: Date.now(),
    coordinationId: crypto.randomUUID(),
  }
}

function notifyOtherTabs(reason: SessionEventType) {
  const event = createSessionEvent(reason)
  sessionChannel?.postMessage(event)
  try {
    // The transient payload carries coordination metadata only, never identity.
    localStorage.setItem(sessionEventKey, JSON.stringify(event))
    localStorage.removeItem(sessionEventKey)
  } catch {
    // This tab still clears correctly when storage is unavailable.
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== sessionEventKey || !event.newValue) return
    try {
      handleExternalEvent(JSON.parse(event.newValue) as unknown)
    } catch {
      // Ignore malformed same-origin coordination messages.
    }
  })
}

if (sessionChannel) {
  sessionChannel.addEventListener('message', (event) => {
    handleExternalEvent(event.data)
  })
}

/** Single in-memory source of truth for the current web access session. */
export const authSessionStore = {
  getSession: () => currentSession,
  getAccessToken: () => currentSession?.accessToken ?? null,
  getGeneration: () => generation,

  replace(session: AuthenticatedSession, invalidateOtherTabs = false) {
    const previousUserId = currentSession?.userId
    if (previousUserId !== session.userId) clearAccountBoundBrowserState()
    currentSession = session
    generation += 1
    publish()
    if (invalidateOtherTabs) notifyOtherTabs('account-change')
  },

  clear(invalidateOtherTabs = false) {
    clearLocalSession()
    if (invalidateOtherTabs) notifyOtherTabs('logout')
  },

  subscribe(listener: SessionListener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

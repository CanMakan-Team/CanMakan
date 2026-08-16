import type { ReactNode } from 'react'

export type PortalIconName =
  | 'home'
  | 'person'
  | 'people'
  | 'gear'
  | 'overview'
  | 'restrictions'
  | 'history'
  | 'trends'

const NAMED_ICONS: PortalIconName[] = [
  'home',
  'person',
  'people',
  'gear',
  'overview',
  'restrictions',
  'history',
  'trends',
]

function iconPaths(name: PortalIconName): ReactNode {
  switch (name) {
    case 'home':
      return (
        <>
          <path d="M3 10.5 12 3l9 7.5" />
          <path d="M5 9.8V21h14V9.8" />
          <path d="M10 21v-7h4v7" />
        </>
      )
    case 'person':
      return (
        <>
          <circle cx="12" cy="8" r="3.5" />
          <path d="M5 20c0-3.6 3.1-6.5 7-6.5s7 2.9 7 6.5" />
        </>
      )
    case 'people':
      return (
        <>
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </>
      )
    case 'gear':
      return (
        <>
          <circle cx="12" cy="12" r="3" />
          <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
        </>
      )
    case 'overview':
      return (
        <>
          <rect x="3" y="3" width="7" height="9" rx="1" />
          <rect x="14" y="3" width="7" height="5" rx="1" />
          <rect x="14" y="12" width="7" height="9" rx="1" />
          <rect x="3" y="16" width="7" height="5" rx="1" />
        </>
      )
    case 'restrictions':
      return (
        <>
          <rect x="8" y="2" width="8" height="4" rx="1" />
          <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
          <path d="M8 11h.01" />
          <path d="M12 11h4" />
          <path d="M8 16h.01" />
          <path d="M12 16h4" />
        </>
      )
    case 'history':
      return (
        <>
          <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
          <path d="M3 3v5h5" />
          <path d="M12 7v5l4 2" />
        </>
      )
    case 'trends':
      return (
        <>
          <path d="M3 3v16a2 2 0 0 0 2 2h16" />
          <path d="m7 14 4-4 4 3 5-6" />
        </>
      )
  }
}

function isPortalIconName(name: string): name is PortalIconName {
  return NAMED_ICONS.includes(name as PortalIconName)
}

/** Stroke icons for portal navigation and matching summary cards. */
export function PortalIcon({
  name,
  className,
}: {
  name: PortalIconName | string
  className?: string
}) {
  if (!isPortalIconName(name)) {
    return <span className={className}>{name}</span>
  }

  return (
    <svg
      className={className}
      data-icon={name}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {iconPaths(name)}
    </svg>
  )
}

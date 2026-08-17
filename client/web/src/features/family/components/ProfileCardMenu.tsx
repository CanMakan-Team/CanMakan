import { useEffect, useRef, useState } from 'react'

type ProfileCardMenuProps = {
  disabled?: boolean
  profileActive: boolean
  profileName: string
  allowLifecycleActions?: boolean
  onEdit: () => void
  onToggleActive: () => void
  onRemove: () => void
}

/**
 * Compact overflow menu for family member card actions.
 */
export function ProfileCardMenu({
  disabled = false,
  profileActive,
  profileName,
  allowLifecycleActions = true,
  onEdit,
  onToggleActive,
  onRemove,
}: ProfileCardMenuProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  const run = (action: () => void) => {
    setOpen(false)
    action()
  }

  return (
    <div className="profile-card__menu" ref={rootRef}>
      <button
        className="icon-button profile-card__menu-trigger"
        type="button"
        disabled={disabled}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Actions for ${profileName}`}
        onClick={() => setOpen((isOpen) => !isOpen)}
      >
        ⋮
      </button>
      {open ? (
        <div className="profile-card__menu-list" role="menu">
          <button role="menuitem" type="button" onClick={() => run(onEdit)}>
            Edit dietary profile
          </button>
          {allowLifecycleActions ? (
            <>
              <button
                className={profileActive ? 'is-warning' : undefined}
                role="menuitem"
                type="button"
                onClick={() => run(onToggleActive)}
              >
                {profileActive ? 'Deactivate' : 'Reactivate'}
              </button>
              <button
                className="is-danger"
                role="menuitem"
                type="button"
                onClick={() => run(onRemove)}
              >
                Remove
              </button>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

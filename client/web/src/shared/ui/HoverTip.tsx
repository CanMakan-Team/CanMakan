import { useId, useState, type ReactNode } from 'react'

/**
 * Shows a short explanation above the wrapped control on hover, keyboard focus, or tap.
 * The wrapper is focusable and toggleable via click/tap so keyboard and touch users
 * can access the same information as mouse users.
 */
export function HoverTip({
  text,
  children,
  className,
  interactiveChild = false,
}: {
  text: string
  children: ReactNode
  className?: string
  /**
   * Set when the wrapped child is already an interactive element (e.g. a
   * button). In that case the wrapper itself must not carry a button role
   * or tab stop, since nesting interactive elements breaks keyboard and
   * screen reader navigation. The tooltip still opens on hover/focus/click
   * because those handlers stay on the wrapper.
   */
  interactiveChild?: boolean
}) {
  const [open, setOpen] = useState(false)
  const tooltipId = useId()
  return (
    <span
      className={`hover-tip${className ? ` ${className}` : ''}`}
      tabIndex={interactiveChild ? undefined : 0}
      role={interactiveChild ? undefined : 'button'}
      aria-describedby={open ? tooltipId : undefined}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
      onClick={() => setOpen((previous) => !previous)}
      onKeyDown={(event) => {
        if (event.key === 'Escape') setOpen(false)
      }}
    >
      {children}
      {open ? (
        <span id={tooltipId} className="hover-tip__bubble" role="tooltip">
          {text}
        </span>
      ) : null}
    </span>
  )
}

import { useState, type ReactNode } from 'react'

/**
 * Shows a short explanation above the wrapped control on hover or focus.
 * The help cursor is applied here so every KPI card and chip uses the same affordance.
 */
export function HoverTip({
  text,
  children,
  className,
}: {
  text: string
  children: ReactNode
  className?: string
}) {
  const [open, setOpen] = useState(false)
  return (
    <span
      className={`hover-tip${className ? ` ${className}` : ''}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      {children}
      {open ? <span className="hover-tip__bubble" role="tooltip">{text}</span> : null}
    </span>
  )
}

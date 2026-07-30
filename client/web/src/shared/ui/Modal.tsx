import {
  useEffect,
  useRef,
  type ReactNode,
  type RefObject,
} from 'react'

interface ModalProps {
  title: string
  description?: string
  children: ReactNode
  onClose: () => void
  labelledBy?: string
  returnFocusRef?: RefObject<HTMLElement | null>
  wide?: boolean
}

export function Modal({
  title,
  description,
  children,
  onClose,
  labelledBy = 'modal-title',
  returnFocusRef,
  wide = false,
}: ModalProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const returnFocusElement = returnFocusRef?.current
    closeButtonRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      returnFocusElement?.focus()
    }
  }, [onClose, returnFocusRef])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className={`modal ${wide ? 'modal--wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal__header">
          <div>
            <p className="eyebrow">CanMakan prototype</p>
            <h2 id={labelledBy}>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button
            ref={closeButtonRef}
            className="icon-button"
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
          >
            ×
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </section>
    </div>
  )
}

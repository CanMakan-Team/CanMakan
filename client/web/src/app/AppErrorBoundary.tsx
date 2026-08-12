import { Component, type ErrorInfo, type ReactNode } from 'react'

interface AppErrorBoundaryState {
  failed: boolean
}

/** Keeps an unexpected render failure from becoming a blank page. */
export class AppErrorBoundary extends Component<
  { children: ReactNode },
  AppErrorBoundaryState
> {
  state: AppErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('CanMakan page render failed', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="centered-page">
        <section className="login-card notice-card" role="alert">
          <span className="brand-mark" aria-hidden="true">CM</span>
          <p className="eyebrow">Page unavailable</p>
          <h1>We could not display this page.</h1>
          <p>Your account is unchanged. Reload the page to try again.</p>
          <button
            className="button button--primary"
            type="button"
            onClick={() => window.location.reload()}
          >
            Reload page
          </button>
        </section>
      </main>
    )
  }
}

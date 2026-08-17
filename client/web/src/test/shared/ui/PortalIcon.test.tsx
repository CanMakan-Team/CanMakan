import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { PortalIcon } from '../../../shared/ui/PortalIcon'

describe('PortalIcon', () => {
  it('renders named stroke icons', () => {
    const { container } = render(
      <>
        <PortalIcon name="home" />
        <PortalIcon name="person" />
        <PortalIcon name="people" />
        <PortalIcon name="gear" />
        <PortalIcon name="overview" />
        <PortalIcon name="restrictions" />
        <PortalIcon name="history" />
        <PortalIcon name="trends" />
      </>,
    )

    expect(container.querySelector('[data-icon="home"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="person"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="people"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="gear"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="overview"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="restrictions"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="history"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="trends"]')).toBeTruthy()
  })

  it('keeps unknown names as text glyphs', () => {
    const { container } = render(<PortalIcon name="↗" />)
    expect(container).toHaveTextContent('↗')
  })
})

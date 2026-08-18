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
        <PortalIcon name="info" />
        <PortalIcon name="clock" />
        <PortalIcon name="activity" />
        <PortalIcon name="chart" />
        <PortalIcon name="message" />
        <PortalIcon name="ban" />
        <PortalIcon name="profile" />
        <PortalIcon name="addPeople" />
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
    expect(container.querySelector('[data-icon="info"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="clock"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="activity"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="chart"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="message"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="ban"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="profile"]')).toBeTruthy()
    expect(container.querySelector('[data-icon="addPeople"]')).toBeTruthy()
  })

  it('keeps unknown names as text glyphs', () => {
    const { container } = render(<PortalIcon name="↗" className="glyph" />)
    expect(container).toHaveTextContent('↗')
    expect(container.querySelector('.glyph')).toBeTruthy()
  })
})

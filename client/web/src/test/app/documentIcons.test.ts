import { describe, expect, it } from 'vitest'
import '../../app/documentIcons'

describe('documentIcons', () => {
  it('sets favicon and apple-touch-icon to the stable launcher URL', () => {
    const icon = document.querySelector('link[rel="icon"]')
    expect(icon).toHaveAttribute('href', '/favicon.webp')
    expect(icon).toHaveAttribute('type', 'image/webp')
    expect(document.querySelector('link[rel="apple-touch-icon"]')).toHaveAttribute(
      'href',
      '/favicon.webp',
    )
  })
})

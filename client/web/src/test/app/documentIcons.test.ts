import { describe, expect, it } from 'vitest'
import launcherIcon from '@launcher/ic_launcher.webp'
import '../../app/documentIcons'

describe('documentIcons', () => {
  it('sets favicon and apple-touch-icon from the Android launcher asset', () => {
    const icon = document.querySelector('link[rel="icon"]')
    expect(icon).toHaveAttribute('href', launcherIcon)
    expect(icon).toHaveAttribute('type', 'image/webp')
    expect(document.querySelector('link[rel="apple-touch-icon"]')).toHaveAttribute(
      'href',
      launcherIcon,
    )
  })
})

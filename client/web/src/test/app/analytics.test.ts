import { afterEach, describe, expect, it, vi } from 'vitest'

const initMock = vi.fn()

vi.mock('@amplitude/analytics-browser', () => ({
  init: (...args: unknown[]) => initMock(...args),
}))

const { initAmplitude } = await import('../../app/analytics')

describe('initAmplitude', () => {
  afterEach(() => {
    initMock.mockClear()
  })

  it('initializes Amplitude when a key is provided', () => {
    initAmplitude('test-key', false)

    expect(initMock).toHaveBeenCalledWith('test-key', {
      defaultTracking: {
        pageViews: true,
        sessions: true,
        formInteractions: false,
        fileDownloads: false,
      },
    })
  })

  it('does not initialize Amplitude when the key is missing', () => {
    initAmplitude(undefined, false)

    expect(initMock).not.toHaveBeenCalled()
  })

  it('warns in dev mode when the key is missing', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    initAmplitude(undefined, true)

    expect(warnSpy).toHaveBeenCalledWith(
      'Amplitude API key is missing. Analytics are disabled for this environment.',
    )
    expect(initMock).not.toHaveBeenCalled()

    warnSpy.mockRestore()
  })

  it('does not warn outside dev mode when the key is missing', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    initAmplitude(undefined, false)

    expect(warnSpy).not.toHaveBeenCalled()

    warnSpy.mockRestore()
  })
})

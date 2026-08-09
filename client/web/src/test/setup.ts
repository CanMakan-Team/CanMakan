import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

/**
 * @author Amelia
 */

afterEach(() => {
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
})

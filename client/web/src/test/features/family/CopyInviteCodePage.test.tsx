import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CopyInviteCodePage } from '../../../features/family/pages/CopyInviteCodePage'

describe('CopyInviteCodePage', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('copies the invite code from the query string', async () => {
    render(
      <MemoryRouter initialEntries={['/invite/copy?code=ABCD1234']}>
        <Routes>
          <Route path="/invite/copy" element={<CopyInviteCodePage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'ABCD1234' })).toBeInTheDocument()
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('ABCD1234')
    })
    expect(
      screen.getByText(/Copied to your clipboard/i),
    ).toBeInTheDocument()
  })
})

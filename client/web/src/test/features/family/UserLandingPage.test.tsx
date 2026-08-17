import { describe, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { UserLandingPage } from '../../../features/family/pages/UserLandingPage'

function renderLanding() {
  return render(
    <MemoryRouter initialEntries={['/family']}>
      <Routes>
        <Route path="/family" element={<UserLandingPage />} />
        <Route path="/me" element={<p>Personal home</p>} />
        <Route path="/family/dashboard" element={<p>Family dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('UserLandingPage', () => {
  it('sends every USER, including family admin bookmarks, to personal home', async () => {
    renderLanding()
    expect(await screen.findByText('Personal home')).toBeInTheDocument()
    expect(screen.queryByText('Family dashboard')).not.toBeInTheDocument()
  })
})

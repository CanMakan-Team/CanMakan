import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SystemDashboardPage } from '../../../features/admin/SystemDashboardPage'
import { adminService } from '../../../features/admin/adminService'

vi.mock('../../../features/admin/adminService', () => ({
  adminService: {
    getConsumerTrends: vi.fn(),
    getUsers: vi.fn(),
  },
}))

describe('SystemDashboardPage UC13 account summary', () => {
  beforeEach(() => {
    vi.mocked(adminService.getConsumerTrends).mockReset()
    vi.mocked(adminService.getUsers).mockReset()
  })

  it('counts suspended accounts from active=false', async () => {
    vi.mocked(adminService.getConsumerTrends).mockResolvedValue({
      period: { from: '2026-08-01', to: '2026-08-10' },
      verdictDistribution: [],
      flaggedIngredients: [],
      partial: false,
    })
    vi.mocked(adminService.getUsers).mockResolvedValue([
      {
        userId: 21,
        email: 'active@example.test',
        role: 'USER',
        active: true,
        updatedAt: '2026-08-10T09:30:00',
      },
      {
        userId: 22,
        email: 'suspended@example.test',
        role: 'USER',
        active: false,
        updatedAt: '2026-08-10T09:30:00',
      },
      {
        userId: 23,
        email: 'admin@example.test',
        role: 'ADMIN',
        active: false,
        updatedAt: '2026-08-10T09:30:00',
      },
    ])

    render(
      <MemoryRouter>
        <SystemDashboardPage />
      </MemoryRouter>,
    )

    const label = await screen.findByText('Suspended accounts')
    expect(label.parentElement).toHaveTextContent('2')
    expect(screen.queryByText('Pending access')).not.toBeInTheDocument()
  })
})

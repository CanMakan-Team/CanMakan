import { test, expect, type Page, type Route } from '@playwright/test'

/**
 * Phase 6 smoke: login, one family page, one admin analytics page.
 * API responses are mocked so the suite does not require a live backend.
 */

const personalHomeHeading = /Good (morning|afternoon|evening),/i

const consumerTrendsPayload = {
  generatedAt: '2026-08-18T10:00:00+08:00',
  period: { from: '2026-07-19', to: '2026-08-18', timezone: 'Asia/Singapore' },
  appliedFilters: { category: null },
  summary: {
    totalScans: 12,
    safeCount: 8,
    warningCount: 3,
    unsafeCount: 1,
    uniqueProducts: 6,
    averageScansPerDay: 0.4,
    peakScanDay: { date: '2026-08-10', scanCount: 4 },
  },
  dailyTrend: [
    {
      date: '2026-08-10',
      totalCount: 4,
      safeCount: 3,
      warningCount: 1,
      unsafeCount: 0,
    },
  ],
  mostScannedProducts: [
    { rank: 1, productName: 'Oat milk', scanCount: 4, percentage: 33.3 },
  ],
  categoryOverview: [{ category: 'Dairy', scanCount: 4, percentage: 33.3 }],
  topRestrictions: [{ restrictionCode: 'DAIRY', flaggedCount: 2 }],
  topFlaggedIngredients: [{ ingredientName: 'Milk', flaggedCount: 2 }],
  dataQuality: { partial: false, skippedMalformedFindings: 0 },
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

async function mockFamilyApis(page: Page) {
  await page.route('**/api/families/me', (route) =>
    json(route, 200, {
      familyId: 1,
      familyName: 'Test Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 1,
      createdByUserId: 1,
    }),
  )
  await page.route('**/api/families/me/members', (route) =>
    json(route, 200, [
      {
        memberId: 1,
        profileId: 1,
        profileName: 'David Lim',
        relationship: 'SELF',
        commonRequirements: [],
        restrictions: [],
        source: 'REGISTERED_USER',
        profileActive: true,
        memberRole: 'PRIMARY_ADMIN',
      },
    ]),
  )
  await page.route('**/api/families/me/active-profile', (route) =>
    json(route, 200, { profileId: 1, profileName: 'David Lim' }),
  )
  await page.route('**/api/families/me/scans', (route) => json(route, 200, []))
  await page.route('**/api/restrictions', (route) => json(route, 200, []))
  await page.route('**/api/profiles/me', (route) =>
    json(route, 404, { message: 'No SELF profile exists for this account yet.' }),
  )
}

function sessionBody(user: { userId: number; email: string; role: 'USER' | 'ADMIN' }) {
  return {
    accessToken: `${user.role.toLowerCase()}-token`,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { ...user, active: true },
  }
}

/** Refresh stays 401 until login succeeds, then restores the in-memory session after a full navigation. */
async function mockAuthSession(
  page: Page,
  user: { userId: number; email: string; role: 'USER' | 'ADMIN' },
) {
  let signedIn = false
  await page.route('**/api/auth/refresh', (route) => {
    if (!signedIn) {
      return json(route, 401, { message: 'Unauthorized' })
    }
    return json(route, 200, sessionBody(user))
  })
  await page.route('**/api/auth/login', (route) => {
    signedIn = true
    return json(route, 200, sessionBody(user))
  })
  await page.route('**/api/auth/me', (route) =>
    json(route, 200, { userId: user.userId, email: user.email, role: user.role, active: true }),
  )
}

test.describe('Quality smoke', () => {
  test('family user can sign in and open the family dashboard', async ({ page }) => {
    await mockAuthSession(page, { userId: 1, email: 'test@example.com', role: 'USER' })
    await mockFamilyApis(page)

    await page.goto('/login')
    await page.locator('#family-email').waitFor({ state: 'visible', timeout: 15000 })
    await page.locator('#family-email').fill('test@example.com')
    await page.locator('input[autocomplete="current-password"]').fill('Password123!')
    await page.click('button[type="submit"]')

    await expect(page).toHaveURL(/\/me(?:\/)?(?:\?|$)/, { timeout: 15000 })
    await expect(page.getByRole('heading', { name: personalHomeHeading })).toBeVisible({
      timeout: 15000,
    })

    await page.goto('/family/dashboard')
    await expect(page.getByRole('heading', { name: 'Manage Test Family' })).toBeVisible({
      timeout: 15000,
    })
    await expect(page.getByLabel('Family summary')).toBeVisible()
  })

  test('system admin can sign in and open consumer trends', async ({ page }) => {
    await mockAuthSession(page, { userId: 2, email: 'admin@example.com', role: 'ADMIN' })
    await page.route('**/api/admin/consumer-trends**', (route) =>
      json(route, 200, consumerTrendsPayload),
    )

    await page.goto('/system-admin-login')
    await page.locator('#system-email').waitFor({ state: 'visible', timeout: 15000 })
    await page.locator('#system-email').fill('admin@example.com')
    await page.locator('input[autocomplete="current-password"]').fill('Password123!')
    await page.click('button[type="submit"]')

    await expect(page).toHaveURL(/\/system(?:\/)?(?:\?|$)/, { timeout: 15000 })

    await page.goto('/system/trends')
    await expect(page.getByRole('heading', { name: 'Consumer Trends' })).toBeVisible({
      timeout: 15000,
    })
    await expect(page.getByRole('heading', { name: 'Daily Scan Activity' })).toBeVisible({
      timeout: 15000,
    })
  })
})

import { test, expect } from '@playwright/test';

/**
 * CMK-55 Playwright authentication and route guarding tests for CanMakan web application.
 * This test suite verifies that the authentication flow and route guarding mechanisms work 
 * as expected, ensuring that users are properly redirected based on their authentication status.
**/

test.describe('Authentication and Route Guarding', () => {

  async function mockAuthenticatedUser(page) {
    await page.route('**/api/auth/refresh', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'mock-token',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { userId: 1, email: 'test@example.com', role: 'USER', active: true }
        })
      });
    });

    await page.route('**/api/auth/me', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ userId: 1, email: 'test@example.com', role: 'USER', active: true })
      });
    });

    await page.route('**/api/families/me', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          familyId: 1,
          familyName: 'Test Family',
          memberRole: 'PRIMARY_ADMIN',
          selfProfileId: 1,
          createdByUserId: 1
        })
      });
    });

    await page.route('**/api/families/me/members', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            memberId: 1,
            profileId: 1,
            profileName: 'David Lim',
            relationship: 'SELF',
            ageGroup: 'ADULT',
            commonRequirements: [],
            restrictions: [],
            source: 'REGISTERED_USER',
            profileActive: true,
            memberRole: 'PRIMARY_ADMIN'
          }
        ])
      });
    });

    await page.route('**/api/families/me/active-profile', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ profileId: 1, profileName: 'David Lim' })
      });
    });

    await page.route('**/api/families/me/scans', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });
  }

  test('Unauthenticated Users are Redirected to Login', async ({ page }) => {
    await page.route('**/api/auth/refresh', route => {
      route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Unauthorized' }) });
    });
    await page.goto('/family');
    await expect(page).toHaveURL(/.*\/login/);
  });

  test('Valid Credentials Grant Access to the Portal', async ({ page }) => {
    // 1. Intercept the login POST request only
    await page.route('**/api/auth/login', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'fake-jwt-token',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { userId: 1, email: 'test@example.com', role: 'USER', active: true }
        })
      });
    });

    // 2. Also mock the post-login gates so it succeeds after credentials are submitted
    await page.route('**/api/auth/me', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ userId: 1, email: 'test@example.com', role: 'USER', active: true })
      });
    });

    await page.route('**/api/families/me', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          familyId: 1,
          familyName: 'Test Family',
          memberRole: 'PRIMARY_ADMIN',
          selfProfileId: 1,
          createdByUserId: 1
        })
      });
    });

    await page.route('**/api/families/me/members', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ memberId: 1, profileId: 1, profileName: 'David Lim', relationship: 'SELF', ageGroup: 'ADULT', commonRequirements: [], restrictions: [], source: 'REGISTERED_USER', profileActive: true, memberRole: 'PRIMARY_ADMIN' }])
      });
    });

    await page.route('**/api/families/me/active-profile', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ profileId: 1, profileName: 'David Lim' })
      });
    });

    await page.route('**/api/families/me/scans', route => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    });

    // 3. Start unauthenticated on the login page
    await page.goto('/login');
    
    // 4. Fill credentials into the rendered form
    const emailInput = page.locator('#family-email');
    await emailInput.waitFor({ state: 'visible', timeout: 10000 });
    await emailInput.fill('test@example.com');

    const passwordInput = page.locator('input[autocomplete="current-password"]');
    await passwordInput.fill('Password123!');
    
    await page.click('button[type="submit"]');

    // 5. Verify successful entry into the portal
    await expect(page.locator('h1').filter({ hasText: /Good (morning|afternoon|evening)/ })).toBeVisible({ timeout: 15000 });
  });

  test('Sign Out Clears Session and Redirects to Login', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.route('**/api/auth/logout', route => route.fulfill({ status: 200 }));

    await page.goto('/family');
    await expect(page.locator('h1').filter({ hasText: /Good (morning|afternoon|evening)/ })).toBeVisible({ timeout: 15000 });

    const mobileMenuButton = page.locator('.mobile-header .icon-button');
    if (await mobileMenuButton.isVisible()) {
      await mobileMenuButton.click({ force: true });
      await expect(page.locator('.sidebar')).toHaveClass(/sidebar--open/);
    }
    
    // Ensure the sign-out button is scrolled into view before clicking
    const signOutButton = page.locator('.sidebar__signout');
    await signOutButton.scrollIntoViewIfNeeded();

    await Promise.all([
      page.waitForURL(/.*\/login/, { timeout: 15000 }),
      signOutButton.click({ force: true })
    ]);
  });

  test('Session Persists Across Page Reloads', async ({ page }) => {
    await mockAuthenticatedUser(page);

    await page.goto('/family');
    await expect(page.locator('h1').filter({ hasText: /Good (morning|afternoon|evening)/ })).toBeVisible({ timeout: 15000 });
    
    await page.reload();
    
    await expect(page).toHaveURL(/.*\/family/);
    await expect(page.locator('h1').filter({ hasText: /Good (morning|afternoon|evening)/ })).toBeVisible({ timeout: 15000 });
  });
});
import { test, expect } from "@playwright/test";

/**
 * CMK-55 Playwright responsiveness of CanMakan web application.
 * This test suite checks the responsiveness of the CanMakan web application across different devices and screen sizes.
 */

test('Verify Responsiveness of CanMakan Web Navigation Elements', async ({ page, isMobile }) => {
  // Mock the refresh endpoint so the SessionProvider treats the initial load as unauthenticated.
  await page.route('**/api/auth/refresh', route => {
    route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Unauthorized' }) });
  });

  // Mock the login endpoint to return a valid session.
  await page.route('**/api/auth/login', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'mock-access-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { userId: 1, email: 'david@example.com', role: 'USER', active: true }
      })
    });
  });

  // Mock the post-login endpoints required by the portal.
  await page.route('**/api/auth/me', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: 1, email: 'david@example.com', role: 'USER', active: true })
    });
  });

  await page.route('**/api/families/me', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        familyId: 1,
        familyName: 'David Family',
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
          profileName: 'David',
          relationship: 'SELF',
          ageGroup: 'ADULT',
          commonRequirements: [],
          restrictions: [],
          source: 'REGISTERED_USER',
          profileActive: true
        }
      ])
    });
  });

  await page.route('**/api/families/me/active-profile', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ profileId: 1, profileName: 'David' })
    });
  });

  await page.route('**/api/families/me/scans', route => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  // 1. Go to login page
  await page.goto('/family-login');

  // 2. Fill in credentials and submit
  await page.fill('input[type="email"]', 'david@example.com');
  await page.fill('input[type="password"]', 'Password@123');
  await page.click('button[type="submit"]');

  // 3. Wait until navigated into the protected portal
  await page.waitForURL('**/family');

  // 4. Assert layout based on viewport size
  if (isMobile) {
    const mobileHeader = page.locator('.mobile-header');
    await expect(mobileHeader).toBeVisible();

    const sidebar = page.locator('.sidebar');
    await expect(sidebar).not.toBeInViewport();
  } else {
    const sidebar = page.locator('.sidebar');
    await expect(sidebar).toBeVisible();
  }
});
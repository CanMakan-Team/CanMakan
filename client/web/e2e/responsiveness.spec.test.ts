import { test, expect } from "@playwright/test";

/**
 * CMK-55 Playwright responsiveness of CanMakan web application.
 * This test suite checks the responsiveness of the CanMakan web application across different devices and screen sizes.
 */

test('Verify Responsiveness of CanMakan Web Navigation Elements', async ({ page, isMobile }) => {
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
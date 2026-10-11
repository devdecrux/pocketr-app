import { expect, test } from '@playwright/test'

test('an anonymous visit to the app root lands on the sign-in page', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Welcome back!' })).toBeVisible()
})

import { expect, test } from '@playwright/test'
import { expectNoHorizontalOverflow, watchPageQuality } from './helpers/quality.js'

test('administrator invitation panel requires step-up and stays responsive', async ({ page }, testInfo) => {
  const assertQuality = watchPageQuality(page)
  await page.goto('/profile')

  const panel = page.locator('.invitation-admin-panel')
  await expect(panel).toBeVisible()
  await expect(panel.getByText('仅管理员', { exact: true })).toBeVisible()

  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'GET'
    && response.url().endsWith('/api/admin/invitation-code'))
  await panel.getByRole('button', { name: '查看邀请码' }).click()
  const stepUpDialog = page.getByRole('dialog', { name: '二次验证' })
  await expect(stepUpDialog).toBeVisible()
  await stepUpDialog.locator('input[type="password"]').fill('e2e_password_123')
  await stepUpDialog.getByRole('button', { name: '验证', exact: true }).click()

  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect(response.headers()['cache-control']).toContain('no-store')
  await expect(panel.locator('.invitation-code-input input')).toHaveValue('e2e-invitation-code')

  await page.screenshot({ path: testInfo.outputPath('profile-admin-desktop.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await expect(panel).toBeVisible()
  await panel.scrollIntoViewIfNeeded()
  await expectNoHorizontalOverflow(page)
  const panelBox = await panel.boundingBox()
  expect(panelBox.x).toBeGreaterThanOrEqual(0)
  expect(panelBox.x + panelBox.width).toBeLessThanOrEqual(390)
  await page.screenshot({ path: testInfo.outputPath('profile-admin-mobile.png'), fullPage: true })
  await assertQuality()
})

test('regular users do not render administrator invitation controls', async ({ page }) => {
  const loginResponse = await page.request.post('/api/v2/auth/login', {
    headers: { 'X-Device-Name': 'Playwright regular user' },
    data: { username: 'e2e_user_b', password: 'e2e_password_123' }
  })
  const login = await loginResponse.json()
  expect(loginResponse.ok()).toBe(true)
  expect(login.code).toBe(200)

  await page.goto('/')
  await page.evaluate(({ token, userInfo }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  }, login.data)
  await page.reload()
  await page.goto('/profile')

  await expect(page.locator('.invitation-admin-panel')).toHaveCount(0)
})

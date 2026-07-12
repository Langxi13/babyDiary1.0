import { expect, test } from '@playwright/test'

test('switching accounts does not expose the previous account anniversary cache', async ({ page }) => {
  const accountAAnniversary = '仅属于测试账号 A 的纪念日'
  await page.goto('/')
  const accountAToken = await page.evaluate(() => localStorage.getItem('token'))
  const createResponse = await page.request.post('/api/anniversaries', {
    headers: { Authorization: `Bearer ${accountAToken}` },
    data: {
      title: accountAAnniversary,
      date: '2026-07-12',
      description: '用于验证切换账号后不会显示旧账号缓存。',
      coverImagePath: '',
      sort: 0
    }
  })
  expect(createResponse.ok()).toBe(true)
  const created = await createResponse.json()

  await page.goto('/anniversaries')
  await expect(page.getByText(accountAAnniversary, { exact: true })).toBeVisible()

  const deleteResponse = await page.request.delete(`/api/anniversaries/${created.data.anniversaryId}`, {
    headers: { Authorization: `Bearer ${accountAToken}` }
  })
  expect(deleteResponse.ok()).toBe(true)

  await page.locator('.navbar-user .user-info').click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.getByRole('textbox', { name: '用户名' }).fill('e2e_user_b')
  await page.locator('input[type="password"]').first().fill('e2e_password_123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)

  await page.locator('.desktop-more-menu').hover()
  await page.locator('.desktop-more-item').filter({ hasText: '纪念日' }).click()
  await expect(page).toHaveURL(/\/anniversaries$/)
  await expect(page.getByText(accountAAnniversary, { exact: true })).toHaveCount(0)
  await expect(page.getByText('暂无纪念日', { exact: true })).toBeVisible()
})

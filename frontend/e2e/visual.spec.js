import { expect, test } from '@playwright/test'

async function settle(page, path) {
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.el-loading-mask')).toHaveCount(0)
}

test('critical desktop layouts match reviewed snapshots', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Visual baselines are reviewed once on Chromium.')
  await page.setViewportSize({ width: 1366, height: 900 })

  await settle(page, '/')
  await expect(page).toHaveScreenshot('home-desktop.png', {
    fullPage: true,
    animations: 'disabled',
    mask: [page.locator('.eyebrow')],
    maxDiffPixelRatio: 0.01
  })

  await settle(page, '/diaries')
  await expect(page).toHaveScreenshot('diaries-desktop.png', {
    fullPage: true,
    animations: 'disabled',
    mask: [page.locator('.created-time')],
    maxDiffPixelRatio: 0.01
  })

  await settle(page, '/album')
  await expect(page).toHaveScreenshot('album-desktop.png', { fullPage: true, animations: 'disabled', maxDiffPixelRatio: 0.01 })
})

test('critical mobile layouts match reviewed snapshots', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Visual baselines are reviewed once on Chromium.')
  await page.setViewportSize({ width: 390, height: 844 })

  await settle(page, '/')
  await expect(page).toHaveScreenshot('home-mobile.png', {
    animations: 'disabled',
    mask: [page.locator('.eyebrow')],
    maxDiffPixelRatio: 0.01
  })

  await settle(page, '/diaries')
  await expect(page).toHaveScreenshot('diaries-mobile.png', {
    animations: 'disabled',
    mask: [page.locator('.created-time')],
    maxDiffPixelRatio: 0.01
  })
})

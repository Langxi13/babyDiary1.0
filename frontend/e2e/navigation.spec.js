import { expect, test } from '@playwright/test'
import { expectNoHorizontalOverflow, watchPageQuality } from './helpers/quality.js'

test('@smoke desktop navigation remains complete without overlap', async ({ page }) => {
  const assertQuality = watchPageQuality(page)

  for (const width of [1281, 1366, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.locator('.navbar-menu')).toBeVisible()
    await expect(page.locator('.navbar-user')).toBeVisible()
    await expect(page.locator('.desktop-primary-item')).toHaveCount(5)
    await expect(page.locator('.desktop-more-menu > .el-sub-menu__title')).toContainText('更多')

    const brand = await page.locator('.navbar-brand').boundingBox()
    const menu = await page.locator('.navbar-menu').boundingBox()
    const user = await page.locator('.navbar-user').boundingBox()
    expect(brand.x + brand.width).toBeLessThanOrEqual(menu.x)
    expect(menu.x + menu.width).toBeLessThanOrEqual(user.x)
    await expectNoHorizontalOverflow(page)
  }

  await page.locator('.desktop-more-menu > .el-sub-menu__title').hover()
  const morePopup = page.locator('.el-menu--popup').filter({ hasText: '时间轴' })
  await expect(morePopup).toContainText('时间轴')
  await expect(morePopup).toContainText('日历')
  await expect(morePopup).toContainText('纪念日')
  await expect(morePopup).toContainText('AI 报告')
  await expect(morePopup).toContainText('草稿')
  await assertQuality()
})

test('@smoke compact navigation takes over at the desktop boundary', async ({ page }) => {
  const assertQuality = watchPageQuality(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await expect(page.locator('.navbar-menu')).toBeHidden()
  await expect(page.locator('.mobile-menu')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await assertQuality()
})

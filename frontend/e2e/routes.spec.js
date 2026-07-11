import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { expectNoHorizontalOverflow, watchPageQuality } from './helpers/quality.js'

const routes = [
  ['首页', '/'],
  ['日记', '/diaries'],
  ['共同空间', '/spaces'],
  ['时间轴', '/timeline'],
  ['日历', '/calendar'],
  ['纪念日', '/anniversaries'],
  ['相册', '/album'],
  ['AI 报告', '/ai-reports'],
  ['草稿', '/drafts'],
  ['个人信息', '/profile'],
  ['通知', '/notifications'],
  ['写日记', '/diaries/create']
]

for (const [name, path] of routes) {
  test(`@smoke ${name} route has no browser or server failure`, async ({ page }) => {
    const assertQuality = watchPageQuality(page)
    await page.goto(path)
    await expect(page).not.toHaveURL(/\/login/)
    await expect(page.locator('#app')).toBeVisible()
    await expectNoHorizontalOverflow(page)
    if (path === '/album') {
      await expect(page.getByRole('heading', { name: '相册', exact: true })).toBeVisible()
      await expect(page.locator('.album-load-error')).toHaveCount(0)
    }
    await assertQuality()
  })
}

test('critical pages have no serious accessibility violations', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Axe runs once in Chromium; layout still runs in every engine.')
  for (const path of ['/', '/diaries', '/album', '/profile']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.el-loading-mask')).toHaveCount(0)
    await expect(page.locator('.el-zoom-in-center-enter-active, .el-zoom-in-center-leave-active')).toHaveCount(0)
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze()
    const serious = results.violations.filter(item => ['serious', 'critical'].includes(item.impact))
    expect(serious, `${path}: ${serious.map(item => item.id).join(', ')}`).toEqual([])
  }
})

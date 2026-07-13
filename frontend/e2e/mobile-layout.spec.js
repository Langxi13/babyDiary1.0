import { expect, test } from '@playwright/test'
import { expectNoHorizontalOverflow, watchPageQuality } from './helpers/quality.js'

const routes = [
  ['home', '/'],
  ['diaries', '/diaries'],
  ['spaces', '/spaces'],
  ['timeline', '/timeline'],
  ['calendar', '/calendar'],
  ['anniversaries', '/anniversaries'],
  ['album', '/album'],
  ['ai-reports', '/ai-reports'],
  ['drafts', '/drafts'],
  ['profile', '/profile'],
  ['notifications', '/notifications'],
  ['diary-create', '/diaries/create']
]

const widths = [320, 390, 430]

const layoutIssues = async page => page.evaluate(() => {
  const viewportWidth = document.documentElement.clientWidth
  const issues = []
  const visible = element => {
    const style = getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0
  }

  const boundedSelectors = [
    '.mobile-topbar',
    '.mobile-tabbar',
    '.page-container',
    '.page-title-row',
    '.page-header',
    '.filter-section',
    '.panel',
    '.form-panel',
    '.profile-panel',
    '.calendar-panel',
    '.detail-panel'
  ]

  for (const element of document.querySelectorAll(boundedSelectors.join(','))) {
    if (!visible(element)) continue
    const rect = element.getBoundingClientRect()
    if (rect.left < -1 || rect.right > viewportWidth + 1) {
      issues.push(`${element.className}: horizontal bounds ${rect.left.toFixed(1)}..${rect.right.toFixed(1)}`)
    }
  }

  const textControlSelectors = [
    '.el-button',
    '.el-form-item__label',
    '.el-radio-button__inner',
    '.el-tabs__item',
    '.sheet-link-list button',
    '.tabbar-item'
  ]

  for (const element of document.querySelectorAll(textControlSelectors.join(','))) {
    if (!visible(element)) continue
    if (element.scrollWidth > element.clientWidth + 2 || element.scrollHeight > element.clientHeight + 2) {
      issues.push(`${element.className || element.tagName}: clipped control text`)
    }
  }

  return issues
})

test.describe('critical phone layouts stay readable', () => {
  test.describe.configure({ mode: 'serial' })

  for (const width of widths) {
    test(`${width}px supported routes`, async ({ page }, testInfo) => {
      test.skip(testInfo.project.name !== 'chromium', 'The phone layout matrix runs once on Chromium.')
      const assertQuality = watchPageQuality(page)

      await page.setViewportSize({ width, height: 844 })
      for (const [name, path] of routes) {
        await page.goto(path)
        await page.waitForLoadState('networkidle')
        await expect(page.locator('.el-loading-mask')).toHaveCount(0)
        await expectNoHorizontalOverflow(page)
        expect(await layoutIssues(page), `${width}px ${path}`).toEqual([])

        if (process.env.MOBILE_UI_SCREENSHOTS === 'true') {
          await page.screenshot({
            path: testInfo.outputPath(`${width}-${name}.png`),
            fullPage: true,
            animations: 'disabled'
          })
        }
      }

      await assertQuality()
    })
  }
})

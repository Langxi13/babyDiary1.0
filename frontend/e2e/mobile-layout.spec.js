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
  ['about', '/about'],
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
    '.about-surface',
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

test('phone diary list keeps filters collapsed, actions aligned, and detail routes at the top', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The phone diary interaction regression runs once on Chromium.')
  const assertQuality = watchPageQuality(page)
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto('/diaries')
  await page.waitForLoadState('networkidle')

  const filterSection = page.locator('.filter-section')
  const collapsedFilter = await filterSection.boundingBox()
  expect(collapsedFilter?.height).toBeLessThan(110)
  await expect(page.locator('.mobile-filter-panel')).toHaveCount(0)

  const dateTrigger = page.locator('.mobile-filter-trigger').first()
  await dateTrigger.click()
  await expect(page.locator('.mobile-filter-panel')).toBeVisible()
  await expect(dateTrigger).toHaveAttribute('aria-expanded', 'true')
  if (process.env.MOBILE_UI_SCREENSHOTS === 'true') {
    await page.screenshot({
      path: testInfo.outputPath('320-diaries-date-filter.png'),
      animations: 'disabled'
    })
  }
  await dateTrigger.click()
  await expect(page.locator('.mobile-filter-panel')).toHaveCount(0)

  const tagTrigger = page.locator('.mobile-filter-trigger').nth(1)
  await tagTrigger.click()
  await expect(page.locator('.mobile-filter-panel')).toBeVisible()
  if (process.env.MOBILE_UI_SCREENSHOTS === 'true') {
    await page.screenshot({
      path: testInfo.outputPath('320-diaries-tag-filter.png'),
      animations: 'disabled'
    })
  }
  await tagTrigger.click()

  const card = page.locator('.diary-card').first()
  await expect(card).toBeVisible()
  await expect(card.locator('.edit-action')).toBeVisible()
  await expect(card.locator('.delete-action')).toBeVisible()
  const alignment = await card.evaluate(element => {
    const heading = element.querySelector('.diary-heading').getBoundingClientRect()
    const edit = element.querySelector('.edit-action').getBoundingClientRect()
    const remove = element.querySelector('.delete-action').getBoundingClientRect()
    return {
      headingTop: heading.top,
      editTop: edit.top,
      deleteTop: remove.top
    }
  })
  expect(Math.abs(alignment.headingTop - alignment.editTop)).toBeLessThanOrEqual(2)
  expect(Math.abs(alignment.editTop - alignment.deleteTop)).toBeLessThanOrEqual(2)

  await page.evaluate(() => {
    document.body.style.minHeight = '2400px'
    window.scrollTo(0, 900)
  })
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0)
  await card.evaluate(element => element.click())
  await page.waitForURL(/\/diaries\/\d+$/)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
  await expect(page.getByRole('button', { name: '编辑' })).toBeVisible()
  await expect(page.getByRole('button', { name: '删除' })).toBeVisible()
  await page.evaluate(() => { document.body.style.minHeight = '' })
  if (process.env.MOBILE_UI_SCREENSHOTS === 'true') {
    await page.screenshot({
      path: testInfo.outputPath('320-diary-detail.png'),
      fullPage: true,
      animations: 'disabled'
    })
  }
  await assertQuality()
})

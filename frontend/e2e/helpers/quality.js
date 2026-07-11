import { expect } from '@playwright/test'

const ignoredConsoleErrors = [
  /favicon\.ico/i
]

export function watchPageQuality(page) {
  const failures = []

  page.on('pageerror', error => failures.push(`pageerror: ${error.message}`))
  page.on('console', message => {
    if (message.type() !== 'error') return
    if (ignoredConsoleErrors.some(pattern => pattern.test(message.text()))) return
    failures.push(`console: ${message.text()}`)
  })
  page.on('response', response => {
    if (response.status() >= 500) failures.push(`HTTP ${response.status()}: ${response.url()}`)
  })
  page.on('requestfailed', request => {
    const reason = request.failure()?.errorText || 'unknown error'
    if (reason.includes('NS_BINDING_ABORTED') || reason.includes('ERR_ABORTED')) return
    failures.push(`request failed: ${request.method()} ${request.url()} (${reason})`)
  })

  return async () => {
    await expect.poll(() => failures, { timeout: 1_000 }).toEqual([])
  }
}

export async function expectNoHorizontalOverflow(page) {
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth
  }))
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport + 1)
}

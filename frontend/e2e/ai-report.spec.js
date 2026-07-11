import { expect, test } from '@playwright/test'
import { watchPageQuality } from './helpers/quality.js'

test('@smoke AI models and weekly report use the isolated mock provider', async ({ page }) => {
  const assertQuality = watchPageQuality(page)
  await page.goto('/ai-reports')

  await page.getByRole('tab', { name: 'AI 配置' }).click()
  await page.getByRole('button', { name: '加载模型' }).click()
  await expect(page.getByText('已加载 1 个模型')).toBeVisible()

  await page.getByRole('tab', { name: '生成报告' }).click()
  await page.getByRole('button', { name: '生成报告' }).click()
  await expect(page.getByRole('heading', { name: '测试报告' })).toBeVisible()
  await expect(page.getByText('你们一起记录了值得回顾的时刻。')).toBeVisible()
  await assertQuality()
})

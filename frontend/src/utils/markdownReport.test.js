import test from 'node:test'
import assert from 'node:assert/strict'

import { renderMarkdownReport } from './markdownReport.js'

test('renderMarkdownReport renders report headings, lists, and bold text', () => {
  const html = renderMarkdownReport(`# 2026年3月月报

## 本期回顾
本月，我们经历了一次重要的健康检查。

## 重要瞬间
- **3月24日**：九院皮肤科就诊
- 持续护理
`)

  assert.match(html, /<h1>2026年3月月报<\/h1>/)
  assert.match(html, /<h2>本期回顾<\/h2>/)
  assert.match(html, /<p>本月，我们经历了一次重要的健康检查。<\/p>/)
  assert.match(html, /<ul><li><strong>3月24日<\/strong>：九院皮肤科就诊<\/li><li>持续护理<\/li><\/ul>/)
})

test('renderMarkdownReport escapes html from report content', () => {
  const html = renderMarkdownReport('## 小节\n<script>alert(1)</script>\n- <img src=x onerror=alert(1)>')

  assert.doesNotMatch(html, /<script>/)
  assert.doesNotMatch(html, /<img/)
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/)
})

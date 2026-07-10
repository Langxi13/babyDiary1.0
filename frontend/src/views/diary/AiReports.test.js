import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./AiReports.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/AiReports.scss', import.meta.url), 'utf8')
].join('\n')
const verifyScript = readFileSync(new URL('../../../../scripts/verify.sh', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../api/ai.js', import.meta.url), 'utf8')

test('ai reports page uses spacious sections for generate history and config', () => {
  assert.match(source, /class="[^"]*\bai-page-hero\b[^"]*"/)
  assert.match(source, /class="[^"]*\bgenerate-assistant-card\b[^"]*"/)
  assert.match(source, /class="[^"]*\breport-preview-surface\b[^"]*"/)
  assert.match(source, /class="[^"]*\bhistory-panel\b[^"]*"/)
  assert.match(source, /class="[^"]*\bconfig-shell\b[^"]*"/)
  assert.match(source, /\.generate-shell\s*{[\s\S]*?grid-template-columns:\s*minmax\(280px,\s*340px\)\s+minmax\(0,\s*1fr\);[\s\S]*?gap:\s*24px;/)
  assert.match(source, /\.panel\s*{[\s\S]*?border-radius:\s*16px;[\s\S]*?padding:\s*24px;/)
  assert.match(source, /\.config-form\s*{[\s\S]*?gap:\s*24px;/)
})

test('ai report controls avoid cramped spacing on mobile', () => {
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.generate-shell,\s*[\s\S]*?\.config-form\s*{[\s\S]*?grid-template-columns:\s*1fr;[\s\S]*?gap:\s*16px;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.ai-page-hero\s*{[\s\S]*?padding:\s*18px;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.markdown-report\s*{[\s\S]*?padding:\s*18px 16px;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.history-toolbar,\s*[\s\S]*?\.config-actions\s*{[\s\S]*?grid-template-columns:\s*1fr;/)
  assert.match(source, /radio-button\/style\/css\.mjs/)
  assert.match(source, /radio-group\/style\/css\.mjs/)
  assert.match(source, /\.history-toolbar :deep\(\.el-radio-group\)\s*{[\s\S]*?grid-template-columns:\s*repeat\(3,/)
})

test('project verification includes ai reports page tests', () => {
  assert.match(verifyScript, /src\/views\/diary\/AiReports\.test\.js/)
})

test('ai generation shows elapsed progress and respects configured request timeout', () => {
  assert.match(source, /generationElapsed/)
  assert.match(source, /正在读取日记并整理内容/)
  assert.match(source, /startGenerationTimer/)
  assert.match(source, /aiApi\.generateReport\([\s\S]*?configForm\.timeoutSeconds\)/)
  assert.match(apiSource, /const aiRequestTimeout/)
  assert.match(apiSource, /\(seconds \+ 10\) \* 1000/)
})

test('ai history is progressively paged and report deletion requires confirmation', () => {
  assert.match(source, /const hasMoreReports = computed/)
  assert.match(source, /size: 10/)
  assert.match(source, /loadMoreReports/)
  assert.match(source, /<el-popconfirm/)
  assert.match(source, /已显示 \{\{ reports\.length \}\} \/ \{\{ totalReports \}\} 份/)
})

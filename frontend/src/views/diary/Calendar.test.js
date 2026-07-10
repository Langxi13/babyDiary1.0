import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./Calendar.vue', import.meta.url), 'utf8')

test('calendar diary titles are shortened before rendering in day cells', () => {
  assert.match(source, /const MAX_DAY_TITLE_LENGTH = 8/)
  assert.match(source, /const compactDayTitle = \(title\) =>/)
  assert.match(source, /return normalized\.length > MAX_DAY_TITLE_LENGTH/)
  assert.match(source, /compactDayTitle\(dayMap\[data\.day\]\.firstTitle\)/)
})

test('calendar day cells clip diary summaries inside the date grid', () => {
  assert.match(source, /:deep\(\.el-calendar-day\)\s*\{[\s\S]*?overflow:\s*hidden;/)
  assert.match(source, /\.day-cell\s*\{[\s\S]*?overflow:\s*hidden;/)
  assert.match(source, /\.day-title\s*\{[\s\S]*?max-width:\s*100%;/)
  assert.match(source, /\.day-title\s*\{[\s\S]*?text-overflow:\s*ellipsis;/)
  assert.match(source, /\.day-title\s*\{[\s\S]*?white-space:\s*nowrap;/)
})

test('calendar opens diary detail directly when a day has one diary', () => {
  assert.match(source, /if \(!item\) \{\s*return\s*\}/)
  assert.match(source, /if \(item\.count === 1 && item\.firstDiaryId\) \{[\s\S]*?router\.push\(`\/diaries\/\$\{item\.firstDiaryId\}`\)[\s\S]*?return[\s\S]*?\}/)
  assert.match(source, /router\.push\(`\/diaries\?date=\$\{day\}`\)/)
})

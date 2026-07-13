import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./DiaryList.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/DiaryList.scss', import.meta.url), 'utf8'),
  readFileSync(new URL('../../components/diary/DiaryMobileFilters.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('../../components/diary/styles/DiaryMobileFilters.scss', import.meta.url), 'utf8')
].join('\n')
const storeSource = readFileSync(new URL('../../stores/diary.js', import.meta.url), 'utf8')

test('diary list content uses a maximum preview height instead of stretching cards indefinitely', () => {
  assert.match(source, /\.diary-text\s*\{[\s\S]*?max-height:\s*132px;/)
  assert.match(source, /\.diary-text\s*\{[\s\S]*?overflow:\s*hidden;/)
  assert.match(source, /\.diary-text\s*\{[\s\S]*?-webkit-line-clamp:\s*5;/)
})

test('diary list renders plain text previews instead of full rich content', () => {
  assert.match(source, /import\s*\{\s*MOODS,\s*moodColor,\s*moodLabel,\s*stripHtml\s*\}/)
  assert.match(source, /const previewContent = \(diary\) =>/)
  assert.doesNotMatch(source, /v-html="diary\.content"/)
})

test('mobile diary filters use a compact search and disclosure row', () => {
  assert.match(source, /class="mobile-search-input"/)
  assert.match(source, /class="mobile-filter-row"/)
  assert.match(source, /aria-controls="mobile-diary-filter-panel"/)
  assert.match(source, /@click="togglePanel\('date'\)"/)
  assert.match(source, /@click="togglePanel\('tag'\)"/)
  assert.match(source, /@click="togglePanel\('mood'\)"/)
  assert.match(source, /id="mobile-diary-filter-panel"/)
  assert.match(source, /\.filter-section\s*\{[\s\S]*?position:\s*sticky;/)
  assert.match(source, /\.mobile-filter-row\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)\s*40px;/)
  assert.match(source, /\.mobile-date-fields\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
})

test('mobile pagination uses compact layout and stays inside the viewport', () => {
  assert.match(source, /:layout="paginationLayout"/)
  assert.match(source, /:pager-count="pagerCount"/)
  assert.match(source, /const isMobileViewport = ref\(typeof window !== 'undefined' && window\.matchMedia\('\(max-width: 768px\)'\)\.matches\)/)
  assert.match(source, /const paginationLayout = computed\(\(\) => isMobileViewport\.value \? 'prev, pager, next' : 'total, prev, pager, next, jumper'\)/)
  assert.match(source, /const pagerCount = computed\(\(\) => isMobileViewport\.value \? 5 : 7\)/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*\{[\s\S]*?\.pagination-section\s*\{[\s\S]*?overflow:\s*hidden;/)
  assert.match(source, /:deep\(\.el-pagination\)\s*\{[\s\S]*?max-width:\s*100%;/)
})

test('diary list displays dates with Chinese formatting helpers', () => {
  assert.match(source, /import\s*\{\s*formatChineseDate,\s*formatChineseDateTime\s*\}/)
  assert.match(source, /formatChineseDate\(diary\.date\)/)
  assert.match(source, /formatChineseDateTime\(diary\.createdAt\)/)
  assert.match(source, /format="YYYY年MM月DD日"/)
})

test('web diary cards expose detail navigation while preserving card click entry', () => {
  assert.match(source, /class="diary-card"\s+@click="openDiary\(diary\.diaryId\)"/)
  assert.match(source, /<el-button\s+class="view-action"\s+type="primary"\s+size="small"\s+text\s+@click\.stop="openDiary\(diary\.diaryId\)"[\s\S]*?查看详情[\s\S]*?<\/el-button>/)
  assert.match(source, /\.diary-actions\s*\{[\s\S]*?display:\s*flex;/)
  assert.match(source, /\.diary-actions\s*\{[\s\S]*?flex-wrap:\s*nowrap;/)
})

test('mobile diary cards keep edit and delete beside the title metadata', () => {
  assert.match(source, /class="diary-heading"/)
  assert.match(source, /class="edit-action"[\s\S]*?aria-label="编辑日记"/)
  assert.match(source, /class="delete-action"[\s\S]*?aria-label="删除日记"/)
  assert.match(source, /\.diary-header\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)\s*78px;/)
  assert.match(source, /\.diary-actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2,\s*36px\);/)
  assert.match(source, /\.diary-footer\s*\{\s*display:\s*none;/)
})

test('keyword filtering is debounced and stale list responses cannot overwrite current results', () => {
  assert.match(source, /@input="scheduleKeywordFilter"/)
  assert.match(source, /window\.setTimeout\(handleFilter, 350\)/)
  assert.match(source, /window\.clearTimeout\(keywordDebounceTimer\)/)
  assert.match(storeSource, /let diaryListRequestId = 0/)
  assert.match(storeSource, /const requestId = \+\+diaryListRequestId/)
  assert.match(storeSource, /response\.code === 200 && requestId === diaryListRequestId/)
})

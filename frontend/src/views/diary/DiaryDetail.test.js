import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./DiaryDetail.vue', import.meta.url), 'utf8')

test('diary detail keeps edit and delete actions available on mobile', () => {
  assert.match(source, /<el-popconfirm[\s\S]*?@confirm="handleDelete"/)
  assert.match(source, /router\.push\(`\/diaries\/\$\{diaryId\}\/edit`\)/)
  assert.match(source, /const handleDelete = async \(\) =>/)
  assert.match(source, /await diaryApi\.deleteDiary\(diaryId\.value\)/)
  assert.match(source, /\.page-header\s*\{[\s\S]*?position:\s*sticky;/)
  assert.match(source, /\.actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/)
})

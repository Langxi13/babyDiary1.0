import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./Anniversaries.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/Anniversaries.scss', import.meta.url), 'utf8')
].join('\n')
const apiSource = readFileSync(new URL('../../api/experience.js', import.meta.url), 'utf8')

test('anniversary editor uses cover upload instead of filename input', () => {
  assert.match(source, /class="anniversary-dialog"/)
  assert.match(source, /class="anniversary-dialog-body"/)
  assert.match(source, /class="anniversary-cover-panel"/)
  assert.match(source, /class="anniversary-fields-panel"/)
  assert.match(source, /class="cover-upload-card"/)
  assert.match(source, /class="cover-upload-preview"/)
  assert.match(source, /:class="\{ 'has-cover': !!coverPreviewUrl \}"/)
  assert.match(source, /:drag="true"/)
  assert.match(source, /accept="image\/\*"/)
  assert.match(source, /:on-change="handleCoverChange"/)
  assert.match(source, /@click="removeCover"/)
  assert.match(source, /const coverFile = ref\(null\)/)
  assert.match(source, /const coverPreviewUrl = ref\(''\)/)
  assert.match(source, /URL\.createObjectURL\(file\)/)
  assert.match(source, /await anniversaryApi\.uploadCover\(coverFile\.value\)/)
  assert.doesNotMatch(source, /封面图片文件名/)
})

test('anniversary editor spacing is responsive and avoids cramped controls', () => {
  assert.match(source, /\.anniversary-dialog\s*{[\s\S]*?:deep\(\.el-dialog__body\)\s*{[\s\S]*?padding:\s*0\s+24px\s+22px;/)
  assert.match(source, /\.anniversary-dialog-body\s*{[\s\S]*?grid-template-columns:\s*minmax\(220px,\s*0\.9fr\)\s+minmax\(0,\s*1\.1fr\);[\s\S]*?gap:\s*20px;/)
  assert.match(source, /\.anniversary-fields-panel\s*{[\s\S]*?display:\s*grid;[\s\S]*?gap:\s*16px;/)
  assert.match(source, /\.anniversary-form\s*{[\s\S]*?:deep\(\.el-form-item\)\s*{[\s\S]*?margin-bottom:\s*0;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.anniversary-dialog\s*{[\s\S]*?:deep\(\.el-dialog\)\s*{[\s\S]*?width:\s*calc\(100vw - 28px\)\s*!important;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\.anniversary-dialog-body\s*{[\s\S]*?grid-template-columns:\s*1fr;/)
})

test('anniversary api exposes multipart cover upload endpoint', () => {
  assert.match(apiSource, /uploadCover\(file\)/)
  assert.match(apiSource, /const formData = new FormData\(\)/)
  assert.match(apiSource, /formData\.append\('coverFile', file\)/)
  assert.match(apiSource, /request\.post\('\/api\/anniversaries\/cover', formData/)
  assert.match(apiSource, /invalidateApiCache\('anniversaries:'\)/)
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/Profile.scss', import.meta.url), 'utf8')
].join('\n')

test('profile joined date uses Chinese date display helper', () => {
  assert.match(source, /import\s*\{\s*formatChineseDate\s*\}/)
  assert.match(source, /formatChineseDate\(authStore\.userInfo\?\.createdAt\)/)
  assert.doesNotMatch(source, /toLocaleDateString\('zh-CN'\)/)
})

test('profile avatar uses shared original image url helper', () => {
  assert.match(source, /import\s*\{\s*originalImageUrl\s*\}\s*from '@\/utils\/imageUrl'/)
  assert.match(source, /const avatarUrl = computed\(\(\) => originalImageUrl\(authStore\.userInfo\?\.avatarPath\)\)/)
  assert.doesNotMatch(source, /`\/images\/\$\{authStore\.userInfo\.avatarPath\}`/)
})

test('profile avatar upload supports drag and click replacement', () => {
  assert.match(source, /class="avatar-upload-card"/)
  assert.match(source, /:drag="true"/)
  assert.match(source, /accept="image\/\*"/)
  assert.match(source, /:on-change="handleAvatarChange"/)
  assert.match(source, /拖拽或点击选择图片/)
  assert.match(source, /从相册选择图片/)
})

test('profile layout prevents text and controls from overlapping at narrow web widths', () => {
  assert.match(source, /grid-template-columns:\s*minmax\(260px,\s*320px\)\s+minmax\(0,\s*1fr\);/)
  assert.match(source, /\.profile-panel\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(source, /\.profile-copy\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(source, /\.profile-copy\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/)
  assert.match(source, /\.password-form\s*\{[\s\S]*?gap:\s*18px;/)
  assert.match(source, /@media\s*\(max-width:\s*900px\)\s*\{[\s\S]*?\.profile-grid\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
})

test('administrator invitation code stays local, step-up protected and automatically masked', () => {
  assert.match(source, /v-if="isAdmin"/)
  assert.match(source, /authStore\.userInfo\?\.systemRole === 'ADMIN'/)
  assert.match(source, /withAdminStepUp/)
  assert.match(source, /adminApi\.getInvitationCode/)
  assert.match(source, /adminApi\.rotateInvitationCode/)
  assert.match(source, /window\.setTimeout\(clearInvitationCode, 60000\)/)
  assert.match(source, /document\.hidden\) clearInvitationCode\(\)/)
  assert.doesNotMatch(source, /localStorage\.setItem\([^)]*invitation/i)
  assert.doesNotMatch(source, /sessionStorage\.setItem\([^)]*invitation/i)
})

test('administrator invitation controls reflow without overlap on mobile', () => {
  assert.match(source, /\.invitation-code-controls\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)\s+auto\s+auto;/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.invitation-code-controls\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/)
  assert.match(source, /@media\s*\(max-width:\s*420px\)[\s\S]*?\.invitation-code-controls\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
})

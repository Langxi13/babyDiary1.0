import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const loginSource = readFileSync(new URL('./Login.vue', import.meta.url), 'utf8')
const registerSource = readFileSync(new URL('./Register.vue', import.meta.url), 'utf8')
const styleSource = readFileSync(new URL('./styles/Auth.scss', import.meta.url), 'utf8')
const authApiSource = readFileSync(new URL('../../api/auth.js', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8')

test('login and registration share the same compact mobile authentication layout', () => {
  assert.match(loginSource, /styles\/Auth\.scss/)
  assert.match(registerSource, /styles\/Auth\.scss/)
  assert.match(loginSource, /<el-form-item label="用户名"/)
  assert.match(registerSource, /<el-form-item label="邀请码"/)
  assert.match(styleSource, /@media\s*\(max-width:\s*768px\)/)
  assert.match(styleSource, /padding:\s*max\(28px,\s*env\(safe-area-inset-top\)\)/)
})

test('authentication fields avoid mobile zoom and expose useful autocomplete hints', () => {
  assert.match(loginSource, /autocomplete="username"/)
  assert.match(loginSource, /autocomplete="current-password"/)
  assert.match(registerSource, /autocomplete="new-password"/)
  assert.match(styleSource, /:deep\(\.el-input__inner\)\s*\{[\s\S]*?font-size:\s*16px;/)
})

test('authentication pages use the app icon without decorative gradient backgrounds', () => {
  assert.match(loginSource, /src="\/app-icon\.png"/)
  assert.match(registerSource, /src="\/app-icon\.png"/)
  assert.doesNotMatch(styleSource, /linear-gradient/)
})

test('login exposes email and recovery-code password reset flows', () => {
  assert.match(loginSource, /忘记密码或使用恢复码/)
  assert.match(loginSource, /resetToken/)
  assert.match(loginSource, /route\.hash/)
  assert.match(loginSource, /requestPasswordReset/)
  assert.match(loginSource, /recoverPassword/)
  assert.match(authApiSource, /\/api\/v2\/auth\/password\/reset-request/)
  assert.match(authApiSource, /\/api\/v2\/auth\/password\/recover/)
  assert.match(routerSource, /isPasswordReset/)
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./StepUpDialog.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/StepUpDialog.scss', import.meta.url), 'utf8'),
  readFileSync(new URL('../../utils/stepUp.js', import.meta.url), 'utf8')
].join('\n')

test('step-up uses a dedicated password dialog instead of a generic prompt', () => {
  assert.match(source, /class="step-up-dialog"/)
  assert.match(source, /modal-class="step-up-dialog-overlay"/)
  assert.match(source, /当前登录密码/)
  assert.match(source, /show-password/)
  assert.match(source, /autocomplete="current-password"/)
  assert.match(source, /role="alert"/)
  assert.match(source, /openStepUpDialog/)
  assert.doesNotMatch(source, /ElMessageBox\.prompt/)
})

test('step-up dialog has distinct desktop and mobile layouts', () => {
  assert.match(source, /width:\s*min\(430px,\s*calc\(100vw - 32px\)\)\s*!important;/)
  assert.match(source, /@media\s*\(max-width:\s*600px\)/)
  assert.match(source, /align-items:\s*flex-end;/)
  assert.match(source, /env\(safe-area-inset-bottom\)/)
  assert.match(source, /grid-template-columns:\s*1fr 1fr;/)
  assert.match(source, /font-size:\s*16px;/)
})

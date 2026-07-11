import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const foundationSource = readFileSync(new URL('./mobile-foundation.scss', import.meta.url), 'utf8')
const mainSource = readFileSync(new URL('./main.scss', import.meta.url), 'utf8')

test('global mobile foundation is loaded and protects narrow viewports', () => {
  assert.match(mainSource, /@use ['"]\.\/mobile-foundation(?:\.scss)?['"];/)
  assert.match(foundationSource, /min-width:\s*320px;/)
  assert.match(foundationSource, /overflow-x:\s*clip;/)
  assert.match(foundationSource, /--mobile-page-gutter:\s*14px;/)
})

test('mobile controls and overlays stay usable inside safe areas', () => {
  assert.match(foundationSource, /\.el-date-editor\.el-input,[\s\S]*?min-height:\s*44px;/)
  assert.match(foundationSource, /body \.el-dialog\s*\{[\s\S]*?max-height:\s*calc\(100dvh/)
  assert.match(foundationSource, /body \.el-dialog__body\s*\{[\s\S]*?overflow-y:\s*auto;/)
  assert.match(foundationSource, /body \.el-picker__popper,[\s\S]*?max-width:\s*calc\(100vw - 16px\);/)
})

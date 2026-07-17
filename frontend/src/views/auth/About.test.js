import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./About.vue', import.meta.url), 'utf8')

test('about page exposes installed client and server compatibility versions', () => {
  assert.match(source, /版本信息/)
  assert.match(source, /clientVersionLabel/)
  assert.match(source, /serverVersion/)
  assert.match(source, /apiVersion/)
})

test('about page keeps update installation explicit and checksum visible', () => {
  assert.match(source, /updateStore\.openUpdate\(\)/)
  assert.match(source, /APK SHA-256/)
  assert.match(source, /复制下载地址/)
  assert.match(source, /trustedUpdateUrl/)
  assert.match(source, /Android 系统确认安装/)
  assert.doesNotMatch(source, /静默安装[^无]/)
})

test('about page reflows controls and metadata on narrow screens', () => {
  assert.match(source, /@media \(max-width: 768px\)/)
  assert.match(source, /\.update-heading-row \{ align-items: stretch; flex-direction: column; \}/)
  assert.match(source, /\.update-actions \{ align-items: stretch; flex-direction: column; \}/)
  assert.match(source, /overflow-wrap: anywhere/)
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./admin.js', import.meta.url), 'utf8')

test('administrator invitation API sends step-up tokens to no-cache management routes', () => {
  assert.match(source, /'X-Step-Up-Token'/)
  assert.match(source, /request\.get\('\/api\/admin\/invitation-code'/)
  assert.match(source, /request\.post\('\/api\/admin\/invitation-code\/rotate'/)
  assert.doesNotMatch(source, /localStorage|sessionStorage/)
})

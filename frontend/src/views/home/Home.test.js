import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../api/experience.js', import.meta.url), 'utf8')

test('home requests only the favorite photos needed for its preview strip', () => {
  assert.match(source, /photoApi\.page\(\{ favoriteOnly: true, page: 0, size: 6 \}\)/)
  assert.match(source, /response\.data\?\.content/)
  assert.doesNotMatch(source, /photoApi\.list\(\{ favoriteOnly: true \}\)/)
  assert.match(apiSource, /request\.get\('\/api\/photos\/page'/)
  assert.match(source, /Promise\.allSettled/)
})

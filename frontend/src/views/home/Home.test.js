import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../api/home.js', import.meta.url), 'utf8')

test('home uses one bounded projection request', () => {
  assert.match(source, /homeApi\.get\(await requireSpaceId\(\)\)/)
  assert.doesNotMatch(source, /getSystemPhotoPage|fetchDiaries|draftApi|anniversaryApi/)
  assert.match(apiSource, /spaces\/\$\{spaceId\}\/home/)
  assert.match(apiSource, /ttl: options\.ttl \?\? 30000/)
  assert.match(source, /favoritePhotos\.value = result\.favorites/)
})

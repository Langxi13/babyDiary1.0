import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../api/album.js', import.meta.url), 'utf8')

test('home requests only the favorite photos needed for its preview strip', () => {
  assert.match(source, /albumApi\.getSystemPhotoPage\([\s\S]*?'favorites', \{ page: 0, size: 6 \}/)
  assert.match(source, /\)\.content \|\| \[\]/)
  assert.doesNotMatch(source, /getSystemPhotoPage\([\s\S]*?'favorites', \{ page: 0, size: [7-9]/)
  assert.match(apiSource, /albums\/system\/\$\{systemKey\}/)
  assert.match(apiSource, /params: \{ page, size \}/)
  assert.match(source, /Promise\.allSettled/)
})

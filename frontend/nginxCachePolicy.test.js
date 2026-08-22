import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const nginx = readFileSync(new URL('./nginx.conf', import.meta.url), 'utf8')

test('nginx keeps hashed assets immutable and app shell metadata revalidating', () => {
  assert.match(nginx, /~\^\/assets\/ "public, max-age=31536000, immutable"/)
  for (const path of ['index.html', 'sw.js', 'manifest.webmanifest']) {
    assert.match(nginx, new RegExp(`=\\/${path.replace('.', '\\.')}` + ' "no-cache"'))
    assert.match(nginx, new RegExp(`location = \\/${path.replace('.', '\\.')}`))
  }
})

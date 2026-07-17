import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./index.js', import.meta.url), 'utf8')

test('new routes start at the top while browser back restores the saved position', () => {
  assert.match(source, /scrollBehavior\(_to, _from, savedPosition\)/)
  assert.match(source, /if \(savedPosition\) return savedPosition/)
  assert.match(source, /return \{ left: 0, top: 0 \}/)
})

test('about and update page is authenticated and lazily loaded', () => {
  assert.match(source, /About:\s*\(\) => import\('@\/views\/auth\/About\.vue'\)/)
  assert.match(source, /path:\s*'\/about'[\s\S]*?requiresAuth:\s*true/)
})

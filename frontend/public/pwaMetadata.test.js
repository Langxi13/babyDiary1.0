import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
const main = readFileSync(new URL('../src/main.js', import.meta.url), 'utf8')
const manifest = JSON.parse(readFileSync(new URL('./manifest.webmanifest', import.meta.url), 'utf8'))
const serviceWorker = readFileSync(new URL('./sw.js', import.meta.url), 'utf8')

test('html exposes mobile web app metadata for iOS home screen use', () => {
  assert.match(html, /name="apple-mobile-web-app-capable"\s+content="yes"/)
  assert.match(html, /name="apple-mobile-web-app-title"\s+content="Baby Diary"/)
  assert.match(html, /name="apple-mobile-web-app-status-bar-style"\s+content="default"/)
  assert.match(html, /rel="apple-touch-icon"\s+href="\/app-icon\.png"/)
})

test('manifest has installable app shell metadata', () => {
  assert.equal(manifest.display, 'standalone')
  assert.equal(manifest.start_url, '/')
  assert.ok(manifest.icons.some(icon => icon.src === '/app-icon.png' && icon.sizes === '1024x1024' && icon.purpose.includes('maskable')))
})

test('service worker cache version is bumped when the app shell metadata changes', () => {
  assert.match(serviceWorker, /baby-diary-shell-v12/)
  assert.match(serviceWorker, /\/app-icon\.png/)
})

test('app asks the service worker to check for updates after registration', () => {
  assert.match(main, /navigator\.serviceWorker\.register\('\/sw\.js'\)\s*\.then\(registration => registration\.update\(\)\)/)
})

test('manifest and service worker no longer register a system share target', () => {
  assert.equal(manifest.share_target, undefined)
  assert.doesNotMatch(serviceWorker, /SHARE_TARGET|share-target|shared-image/)
})

test('service worker only caches successful same-origin responses and limits shell fallback to navigation', () => {
  assert.match(serviceWorker, /requestUrl\.origin !== self\.location\.origin/)
  assert.match(serviceWorker, /if \(response\.ok\)/)
  assert.match(serviceWorker, /event\.request\.mode === 'navigate'/)
  assert.match(serviceWorker, /return Response\.error\(\)/)
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./MobileAppShell.vue', import.meta.url), 'utf8'),
  readFileSync(new URL('./styles/MobileAppShell.scss', import.meta.url), 'utf8'),
  readFileSync(new URL('../../assets/styles/mobile-foundation.scss', import.meta.url), 'utf8')
].join('\n')

test('mobile app shell keeps desktop navbar and mobile chrome separate', () => {
  assert.match(source, /<nav-bar class="desktop-navbar"/)
  assert.match(source, /class="mobile-topbar"/)
  assert.match(source, /class="mobile-tabbar"/)
})

test('mobile app shell accounts for safe areas and fixed bottom navigation', () => {
  assert.match(source, /env\(safe-area-inset-top\)/)
  assert.match(source, /env\(safe-area-inset-bottom\)/)
  assert.match(source, /--mobile-tabbar-height:\s*66px;/)
  assert.match(source, /\.mobile-shell-content\s*\{[\s\S]*?padding-bottom:\s*calc\(var\(--mobile-tabbar-height\) \+ 14px \+ env\(safe-area-inset-bottom\)\);/)
})

test('mobile app shell exposes secondary links without replacing primary tabs', () => {
  assert.match(source, /MOBILE_PRIMARY_TABS/)
  assert.match(source, /MOBILE_SECONDARY_LINKS/)
  assert.match(source, /secondarySheetOpen/)
})

test('mobile navigation starts loading route chunks on touch before navigation', () => {
  assert.match(source, /import \{ preloadRouteComponent \} from '@\/router'/)
  assert.match(source, /@touchstart\.passive="preload\(tab\.path\)"/)
  assert.match(source, /@touchstart\.passive="preload\(link\.path\)"/)
})

test('mobile tab icons render through explicit element icons for iPhone Safari', () => {
  assert.match(source, /<el-icon class="tabbar-icon">[\s\S]*?<component :is="iconMap\[tab\.icon\]" \/>[\s\S]*?<\/el-icon>/)
  assert.match(source, /\.tabbar-icon\s*\{[\s\S]*?svg\s*\{[\s\S]*?width:\s*1em;/)
})

test('mobile primary write action stays aligned with the bottom tab bar', () => {
  assert.doesNotMatch(source, /translateY\(-10px\)/)
  assert.doesNotMatch(source, /&\.primary\s*\{\s*transform:\s*translateY/)
  assert.match(source, /&\.primary\s*\{[\s\S]*?\.tabbar-icon\s*\{[\s\S]*?width:\s*40px;[\s\S]*?height:\s*40px;/)
})

test('mobile app shell includes install affordance for PWA and iOS home screen', () => {
  assert.match(source, /beforeinstallprompt/)
  assert.match(source, /installSheetOpen/)
  assert.match(source, /添加到桌面/)
  assert.match(source, /Safari/)
})

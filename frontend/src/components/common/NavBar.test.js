import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { DESKTOP_MORE_NAVIGATION, DESKTOP_PRIMARY_NAVIGATION } from '../../config/navigation.js'

const source = readFileSync(new URL('./NavBar.vue', import.meta.url), 'utf8')

test('brand text keeps Baby Diary on one line', () => {
  assert.match(source, /\.navbar-brand\s*\{[\s\S]*?flex-shrink:\s*0;/)
  assert.match(source, /\.brand-text\s*\{[\s\S]*?white-space:\s*nowrap;/)
})

test('navbar separates brand, menu, and user into stable columns', () => {
  assert.match(source, /\.navbar-content\s*\{[\s\S]*?max-width:\s*1440px;/)
  assert.match(source, /\.navbar-content\s*\{[\s\S]*?display:\s*grid;/)
  assert.match(source, /\.navbar-content\s*\{[\s\S]*?grid-template-columns:\s*minmax\(160px,\s*180px\)\s+minmax\(0,\s*1fr\)\s+minmax\(300px,\s*360px\);/)
  assert.match(source, /\.navbar-content\s*\{[\s\S]*?column-gap:\s*28px;/)
  assert.match(source, /\.navbar-brand\s*\{[\s\S]*?justify-self:\s*start;/)
  assert.match(source, /\.navbar-menu\s*\{[\s\S]*?justify-self:\s*center;/)
  assert.match(source, /\.navbar-user\s*\{[\s\S]*?justify-self:\s*end;/)
})

test('desktop user area includes the active workspace switcher', () => {
  assert.match(source, /<space-switcher compact \/>/)
  assert.match(source, /import SpaceSwitcher from '@\/components\/common\/SpaceSwitcher\.vue'/)
})

test('desktop navigation keeps core routes visible and groups secondary routes under more', () => {
  assert.deepEqual(DESKTOP_PRIMARY_NAVIGATION.map(item => item.path), ['/', '/diaries', '/spaces', '/album', '/diaries/create'])
  assert.deepEqual(DESKTOP_MORE_NAVIGATION.map(item => item.path), ['/timeline', '/calendar', '/anniversaries', '/ai-reports', '/drafts'])
  assert.match(source, /v-for="item in desktopPrimaryNavigation"/)
  assert.match(source, /<el-sub-menu index="desktop-more"/)
  assert.match(source, /v-for="item in desktopMoreNavigation"/)
  assert.match(source, /\.navbar-menu\s*\{[\s\S]*?overflow:\s*visible;/)
})

test('desktop navigation switches to compact menu before it can squeeze the brand', () => {
  assert.match(source, /@media\s*\(max-width:\s*1280px\)\s*\{[\s\S]*?\.navbar-menu\s*\{[\s\S]*?display:\s*none;/)
})

test('profile entries use direct router links instead of dropdown command navigation', () => {
  const profileLinks = source.match(/<router-link class="dropdown-route-link" to="\/profile">/g) || []
  assert.equal(profileLinks.length, 2)
  assert.doesNotMatch(source, /command="profile"/)
  assert.doesNotMatch(source, /command="\/profile"/)
  assert.match(source, /else \{[\s\S]*?router\.push\(command\)[\s\S]*?\}/)
})

test('navbar avatar uses shared original image url helper', () => {
  assert.match(source, /import\s*\{\s*originalImageUrl\s*\}\s*from '@\/utils\/imageUrl'/)
  assert.match(source, /const avatarUrl = computed\(\(\) => originalImageUrl\(authStore\.userInfo\?\.avatarPath\)\)/)
  assert.doesNotMatch(source, /`\/images\/\$\{authStore\.userInfo\.avatarPath\}`/)
})

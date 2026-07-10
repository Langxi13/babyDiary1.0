import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./auth.js', import.meta.url), 'utf8')

test('user info refresh is throttled and deduplicated', () => {
  assert.match(source, /const USER_INFO_TTL = 30000/)
  assert.match(source, /if \(userInfoRequest\) return userInfoRequest/)
  assert.match(source, /Date\.now\(\) - lastUserInfoFetchAt < USER_INFO_TTL/)
  assert.match(source, /if \(userInfoRequest === request\)/)
})

test('an obsolete user info response cannot restore a cleared or replaced session', () => {
  assert.match(source, /const requestedToken = token\.value/)
  assert.match(source, /response\.code === 200 && token\.value === requestedToken/)
  assert.doesNotMatch(source, /console\.error\('获取用户信息失败/)
})

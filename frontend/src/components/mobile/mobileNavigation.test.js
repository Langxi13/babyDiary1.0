import assert from 'node:assert/strict'
import test from 'node:test'

import {
  MOBILE_BREAKPOINT,
  MOBILE_PRIMARY_TABS,
  MOBILE_SECONDARY_LINKS,
  getMobileRouteTitle,
  isMobileTabActive,
  isPrimaryMobileTab
} from './mobileNavigation.js'

test('mobile shell is limited to phone width', () => {
  assert.equal(MOBILE_BREAKPOINT, 768)
})

test('mobile primary navigation uses app shell tabs with write as the center action', () => {
  assert.deepEqual(
    MOBILE_PRIMARY_TABS.map(tab => tab.label),
    ['首页', '日记', '写日记', '回忆', '我的']
  )
  assert.equal(MOBILE_PRIMARY_TABS[2].path, '/diaries/create')
  assert.equal(MOBILE_PRIMARY_TABS[2].primary, true)
})

test('mobile secondary links are grouped under memory and account areas', () => {
  const memoryPaths = MOBILE_SECONDARY_LINKS.filter(link => link.group === 'memory').map(link => link.path)
  const accountPaths = MOBILE_SECONDARY_LINKS.filter(link => link.group === 'account').map(link => link.path)

  assert.deepEqual(memoryPaths, ['/spaces', '/album', '/timeline', '/calendar', '/anniversaries'])
  assert.deepEqual(accountPaths, ['/ai-reports', '/drafts', '/profile', '/notifications', '/about'])
})

test('route titles and primary tab detection support nested diary routes', () => {
  assert.equal(getMobileRouteTitle('/diaries/12'), '日记详情')
  assert.equal(getMobileRouteTitle('/diaries/12/edit'), '编辑日记')
  assert.equal(isPrimaryMobileTab('/diaries'), true)
  assert.equal(isPrimaryMobileTab('/diaries/12'), false)
})

test('nested diary routes keep the nearest mobile tab active', () => {
  const diaryTab = MOBILE_PRIMARY_TABS.find(tab => tab.path === '/diaries')
  const writeTab = MOBILE_PRIMARY_TABS.find(tab => tab.path === '/diaries/create')

  assert.equal(isMobileTabActive(diaryTab, '/diaries/12'), true)
  assert.equal(isMobileTabActive(writeTab, '/diaries/12/edit'), true)
})

import { describe, expect, it } from 'vitest'
import {
  APP_NAVIGATION_ITEMS,
  DESKTOP_COMPACT_NAVIGATION,
  DESKTOP_MORE_NAVIGATION,
  DESKTOP_PRIMARY_NAVIGATION
} from './navigation.js'

describe('shared navigation model', () => {
  it('keeps high-frequency routes visible and every desktop destination reachable', () => {
    expect(DESKTOP_PRIMARY_NAVIGATION.map(item => item.path)).toEqual([
      '/', '/diaries', '/spaces', '/album', '/diaries/create'
    ])
    expect(DESKTOP_MORE_NAVIGATION.map(item => item.path)).toEqual([
      '/timeline', '/calendar', '/anniversaries', '/ai-reports', '/drafts'
    ])
    expect(new Set(DESKTOP_COMPACT_NAVIGATION.map(item => item.path)).size).toBe(10)
  })

  it('uses unique ids and paths for application destinations', () => {
    expect(new Set(APP_NAVIGATION_ITEMS.map(item => item.id)).size).toBe(APP_NAVIGATION_ITEMS.length)
    expect(new Set(APP_NAVIGATION_ITEMS.map(item => item.path)).size).toBe(APP_NAVIGATION_ITEMS.length)
  })
})

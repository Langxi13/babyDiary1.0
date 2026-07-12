import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  currentRoute: { value: { path: '/anniversaries', meta: { requiresAuth: true } } }
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: mocks.currentRoute,
    replace: mocks.replace
  }
}))

import { useAuthStore } from './auth'

const account = (userId, username) => ({ userId, username })

describe('cross-tab authentication synchronization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.replace.mockReset()
    mocks.currentRoute.value = { path: '/anniversaries', meta: { requiresAuth: true } }
    localStorage.setItem('token', 'account-a-token')
    localStorage.setItem('userInfo', JSON.stringify(account(1, 'account-a')))
  })

  it('resets the session shell when another tab changes accounts', () => {
    const store = useAuthStore()
    const previousVersion = store.sessionVersion
    localStorage.setItem('userInfo', JSON.stringify(account(2, 'account-b')))
    localStorage.setItem('token', 'account-b-token')

    window.dispatchEvent(new StorageEvent('storage', { key: 'userInfo' }))

    expect(store.userInfo.userId).toBe(2)
    expect(store.token).toBe('account-b-token')
    expect(store.sessionVersion).toBeGreaterThan(previousVersion)
  })

  it('adopts a refreshed token without resetting same-account page state', () => {
    const store = useAuthStore()
    const previousVersion = store.sessionVersion
    localStorage.setItem('token', 'account-a-refreshed-token')

    window.dispatchEvent(new StorageEvent('storage', { key: 'token' }))

    expect(store.token).toBe('account-a-refreshed-token')
    expect(store.sessionVersion).toBe(previousVersion)
  })
})

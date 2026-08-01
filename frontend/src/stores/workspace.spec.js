import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  listSpaces: vi.fn(),
  unread: vi.fn(),
  pendingCount: vi.fn(),
  syncWorkspace: vi.fn()
}))

vi.mock('@/api/workspace', () => ({
  workspaceApi: {
    spaces: { list: mocks.listSpaces },
    notifications: { unread: mocks.unread }
  }
}))
vi.mock('@/utils/offlineDb', () => ({ pendingOfflineCount: mocks.pendingCount }))
vi.mock('@/utils/offlineSync', () => ({ syncWorkspace: mocks.syncWorkspace }))

import { useWorkspaceStore } from './workspace'

describe('workspace initialization', () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset())
    localStorage.clear()
    setActivePinia(createPinia())
    mocks.unread.mockResolvedValue(0)
    mocks.pendingCount.mockResolvedValue(0)
    mocks.syncWorkspace.mockResolvedValue({ conflicts: [], failures: [] })
  })

  it('shares concurrent space loading instead of duplicating Android startup requests', async () => {
    let resolveList
    mocks.listSpaces.mockReturnValue(new Promise(resolve => { resolveList = resolve }))
    const store = useWorkspaceStore()

    const first = store.loadSpaces()
    const second = store.loadSpaces()
    resolveList([{ id: 'space-1', type: 'PERSONAL' }])

    await expect(Promise.all([first, second])).resolves.toEqual([
      [{ id: 'space-1', type: 'PERSONAL' }],
      [{ id: 'space-1', type: 'PERSONAL' }]
    ])
    expect(mocks.listSpaces).toHaveBeenCalledOnce()
    expect(store.activeSpaceId).toBe('space-1')
  })

  it('allows initialization to retry after a transient network failure', async () => {
    mocks.listSpaces
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([{ id: 'space-1', type: 'PERSONAL' }])
    const store = useWorkspaceStore()

    await expect(store.initialize()).rejects.toThrow('network')
    await expect(store.initialize()).resolves.toBeUndefined()

    expect(mocks.listSpaces).toHaveBeenCalledTimes(2)
    expect(mocks.pendingCount).toHaveBeenCalled()
    expect(mocks.unread).toHaveBeenCalledOnce()
  })
})

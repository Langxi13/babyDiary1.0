import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  diaryList: vi.fn(),
  diaryDetail: vi.fn(),
  spaceList: vi.fn(),
  spaceCreate: vi.fn(),
  unread: vi.fn(),
  pendingCount: vi.fn(),
  syncWorkspace: vi.fn()
}))

vi.mock('@/api/diary', () => ({
  diaryApi: {
    getDiaryList: mocks.diaryList,
    getDiary: mocks.diaryDetail,
    createDiary: vi.fn(),
    updateDiary: vi.fn(),
    deleteDiary: vi.fn(),
    exportImages: vi.fn(),
    getTimeline: vi.fn(),
    getCalendar: vi.fn()
  }
}))

vi.mock('@/api/workspace', () => ({
  workspaceApi: {
    spaces: { list: mocks.spaceList, create: mocks.spaceCreate },
    notifications: { unread: mocks.unread }
  }
}))

vi.mock('@/utils/offlineDb', () => ({ pendingOfflineCount: mocks.pendingCount }))
vi.mock('@/utils/offlineSync', () => ({ syncWorkspace: mocks.syncWorkspace }))

import { useDiaryStore } from './diary'
import { useWorkspaceStore } from './workspace'
import { resetClientSession } from '@/utils/sessionBoundary'

const resetSession = () => window.dispatchEvent(new CustomEvent('auth:session-reset'))

describe('authenticated store session boundaries', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(mocks).forEach(mock => mock.mockReset())
    mocks.pendingCount.mockResolvedValue(0)
    mocks.unread.mockResolvedValue({ data: 0 })
    mocks.syncWorkspace.mockResolvedValue({ conflicts: [], failures: [] })
  })

  it('clears diary and workspace data immediately when the account session resets', () => {
    const diaryStore = useDiaryStore()
    const workspaceStore = useWorkspaceStore()
    diaryStore.diaries = [{ diaryId: 1, title: 'account A diary' }]
    diaryStore.currentDiary = { diaryId: 1 }
    diaryStore.pagination = { pageNumber: 2, pageSize: 5, totalElements: 12, totalPages: 3 }
    workspaceStore.spaces = [{ spaceId: 'space-a' }]
    workspaceStore.activeSpaceId = 'space-a'
    workspaceStore.pendingCount = 3
    workspaceStore.conflictCount = 2
    workspaceStore.unreadNotifications = 4
    localStorage.setItem('activeSpaceId', 'space-a')

    resetSession()

    expect(diaryStore.diaries).toEqual([])
    expect(diaryStore.currentDiary).toBeNull()
    expect(diaryStore.pagination.totalElements).toBe(0)
    expect(workspaceStore.spaces).toEqual([])
    expect(workspaceStore.activeSpaceId).toBe('')
    expect(workspaceStore.pendingCount).toBe(0)
    expect(workspaceStore.conflictCount).toBe(0)
    expect(workspaceStore.unreadNotifications).toBe(0)
    expect(localStorage.getItem('activeSpaceId')).toBeNull()
  })

  it('clears account-bound browser storage at the shared session boundary', () => {
    localStorage.setItem('activeSpaceId', 'space-a')
    sessionStorage.setItem('stepUpToken', 'temporary-token')
    sessionStorage.setItem('stepUpExpiresAt', '9999999999999')

    resetClientSession('test-reset')

    expect(localStorage.getItem('activeSpaceId')).toBeNull()
    expect(sessionStorage.getItem('stepUpToken')).toBeNull()
    expect(sessionStorage.getItem('stepUpExpiresAt')).toBeNull()
  })

  it('does not let an account A diary request repopulate the store after reset', async () => {
    let resolveRequest
    mocks.diaryList.mockReturnValue(new Promise(resolve => { resolveRequest = resolve }))
    const diaryStore = useDiaryStore()
    const pending = diaryStore.fetchDiaries()

    resetSession()
    resolveRequest({
      code: 200,
      data: {
        content: [{ diaryId: 1, title: 'account A diary' }],
        pageNumber: 0,
        pageSize: 5,
        totalElements: 1,
        totalPages: 1
      }
    })
    await pending

    expect(diaryStore.diaries).toEqual([])
    expect(diaryStore.pagination.totalElements).toBe(0)
  })

  it('does not let an account A space request repopulate the store after reset', async () => {
    let resolveRequest
    mocks.spaceList.mockReturnValue(new Promise(resolve => { resolveRequest = resolve }))
    const workspaceStore = useWorkspaceStore()
    const pending = workspaceStore.loadSpaces()

    resetSession()
    resolveRequest({ data: [{ spaceId: 'space-a', name: 'account A space' }] })
    await pending

    expect(workspaceStore.spaces).toEqual([])
    expect(workspaceStore.activeSpaceId).toBe('')
  })
})

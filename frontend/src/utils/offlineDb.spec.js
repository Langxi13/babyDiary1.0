import { beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'

const DB_NAME = 'baby-diary-offline'

function setAccount(id) {
  localStorage.setItem('userInfo', JSON.stringify({ id }))
  localStorage.setItem('token', `token-${id}`)
}

async function openFreshModule() {
  vi.resetModules()
  return import('@/utils/offlineDb')
}

describe('offline account boundaries', () => {
  beforeEach(() => {
    globalThis.indexedDB = new IDBFactory()
    localStorage.clear()
    setAccount('account-a')
  })

  it('keeps operations and caches isolated by account and strips ephemeral media URLs', async () => {
    const db = await openFreshModule()
    await db.queueOfflineOperation({ kind: 'diary', spaceId: 'space-a', entityId: 'diary-a' })
    await db.setOfflineCache('diaries', {
      items: [{
        id: 'diary-a',
        title: 'A',
        representations: {
          original: { variantType: 'ORIGINAL', profile: 'source', url: '/signed-url', expiresAt: 'soon' }
        }
      }, {
        id: 'locked-diary',
        title: 'Private title',
        diaryDate: '2026-07-30',
        contentHtml: '<p>Private body</p>',
        contentText: 'Private body',
        mood: 'happy',
        locked: true,
        tags: [{ id: 'tag-1' }],
        media: [{ id: 'media-1' }]
      }]
    })

    setAccount('account-b')
    expect(await db.listOfflineOperations()).toHaveLength(0)
    expect(await db.getOfflineCache('diaries')).toBeNull()

    await db.queueOfflineOperation({ kind: 'diary', spaceId: 'space-b', entityId: 'diary-b' })
    expect(await db.listOfflineOperations()).toHaveLength(1)

    setAccount('account-a')
    expect(await db.listOfflineOperations()).toHaveLength(1)
    expect(await db.getOfflineCache('diaries')).toEqual({
      items: [{
        id: 'diary-a',
        title: 'A',
        representations: { original: { variantType: 'ORIGINAL', profile: 'source' } }
      }, {
        id: 'locked-diary',
        title: null,
        diaryDate: '2026-07-30',
        contentHtml: null,
        contentText: null,
        mood: null,
        locked: true,
        tags: [],
        media: []
      }]
    })
    await db.clearOfflineSessionCache('user:account-a')
    expect(await db.getOfflineCache('diaries')).toBeNull()
    expect(await db.listOfflineOperations()).toHaveLength(1)
  })

  it('preserves account-scoped operations while upgrading the supported schema', async () => {
    await createV2Database()
    const db = await openFreshModule()

    expect(await db.listOfflineOperations()).toEqual([
      expect.objectContaining({ id: 'scoped-operation', accountScope: 'user:account-a' })
    ])
  })
})

function createV2Database() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 2)
    request.onupgradeneeded = () => {
      const database = request.result
      const operations = database.createObjectStore('operations', { keyPath: 'id' })
      operations.createIndex('accountScope', 'accountScope', { unique: false })
      operations.createIndex('scopeSpace', ['accountScope', 'spaceId'], { unique: false })
      operations.createIndex('createdAt', 'createdAt', { unique: false })
      database.createObjectStore('meta', { keyPath: 'key' })
      database.createObjectStore('cache', { keyPath: 'key' })
      database.createObjectStore('quarantine', { keyPath: 'id' })
      operations.put({
        id: 'scoped-operation',
        accountScope: 'user:account-a',
        spaceId: 'space-a',
        kind: 'diary',
        createdAt: 1
      })
    }
    request.onsuccess = () => {
      request.result.close()
      resolve()
    }
    request.onerror = () => reject(request.error)
  })
}

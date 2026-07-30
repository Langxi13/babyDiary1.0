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
      items: [{ id: 'diary-a', contentUrl: '/signed-url', thumbnailUrl: '/signed-thumb', title: 'A' }]
    })

    setAccount('account-b')
    expect(await db.listOfflineOperations()).toHaveLength(0)
    expect(await db.getOfflineCache('diaries')).toBeNull()

    await db.queueOfflineOperation({ kind: 'diary', spaceId: 'space-b', entityId: 'diary-b' })
    expect(await db.listOfflineOperations()).toHaveLength(1)

    setAccount('account-a')
    expect(await db.listOfflineOperations()).toHaveLength(1)
    expect(await db.getOfflineCache('diaries')).toEqual({
      items: [{ id: 'diary-a', title: 'A' }]
    })
    await db.clearOfflineSessionCache('user:account-a')
    expect(await db.getOfflineCache('diaries')).toBeNull()
    expect(await db.listOfflineOperations()).toHaveLength(1)
  })

  it('quarantines v1 operations that have no account scope during upgrade', async () => {
    await createV1Database()
    const db = await openFreshModule()

    expect(await db.listOfflineOperations()).toHaveLength(0)
    expect(await db.listQuarantinedOfflineOperations()).toEqual([
      expect.objectContaining({ id: 'legacy-operation', reason: 'legacy-account-scope-unknown' })
    ])
  })
})

function createV1Database() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => {
      const database = request.result
      const operations = database.createObjectStore('operations', { keyPath: 'id' })
      database.createObjectStore('meta', { keyPath: 'key' })
      database.createObjectStore('cache', { keyPath: 'key' })
      operations.put({ id: 'legacy-operation', kind: 'diary', createdAt: 1 })
    }
    request.onsuccess = () => {
      request.result.close()
      resolve()
    }
    request.onerror = () => reject(request.error)
  })
}

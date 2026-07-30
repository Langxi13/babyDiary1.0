import { mergeQueuedDiaryOperation } from '@/utils/offlineQueue'
import { getAccountCacheScope } from '@/utils/sessionScope'

const DB_NAME = 'baby-diary-offline'
const DB_VERSION = 2
const OPERATIONS = 'operations'
const META = 'meta'
const CACHE = 'cache'
const QUARANTINE = 'quarantine'

let dbPromise

function openDb() {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null)
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = event => {
      const db = request.result
      const transaction = request.transaction
      const quarantine = db.objectStoreNames.contains(QUARANTINE)
        ? transaction.objectStore(QUARANTINE)
        : db.createObjectStore(QUARANTINE, { keyPath: 'id' })

      if (!db.objectStoreNames.contains(OPERATIONS)) {
        const store = db.createObjectStore(OPERATIONS, { keyPath: 'id' })
        createOperationIndexes(store)
      } else {
        const store = transaction.objectStore(OPERATIONS)
        createOperationIndexes(store)
        if (event.oldVersion < 2) {
          const cursorRequest = store.openCursor()
          cursorRequest.onsuccess = () => {
            const cursor = cursorRequest.result
            if (!cursor) return
            if (!cursor.value.accountScope) {
              quarantine.put({ ...cursor.value, quarantinedAt: Date.now(), reason: 'legacy-account-scope-unknown' })
              cursor.delete()
            }
            cursor.continue()
          }
        }
      }
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META, { keyPath: 'key' })
      else if (event.oldVersion < 2) transaction.objectStore(META).clear()
      if (!db.objectStoreNames.contains(CACHE)) db.createObjectStore(CACHE, { keyPath: 'key' })
      else if (event.oldVersion < 2) transaction.objectStore(CACHE).clear()
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return dbPromise
}

function createOperationIndexes(store) {
  if (!store.indexNames.contains('accountScope')) store.createIndex('accountScope', 'accountScope', { unique: false })
  if (!store.indexNames.contains('scopeSpace')) store.createIndex('scopeSpace', ['accountScope', 'spaceId'], { unique: false })
  if (!store.indexNames.contains('createdAt')) store.createIndex('createdAt', 'createdAt', { unique: false })
}

function currentScope() {
  const scope = getAccountCacheScope()
  if (scope === 'anonymous' || scope === 'authenticated:unresolved') {
    throw new Error('无法确定当前离线数据所属账户')
  }
  return scope
}

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

export async function queueOfflineOperation(operation) {
  const accountScope = currentScope()
  const value = { ...operation, accountScope, id: operation.id || crypto.randomUUID(),
    createdAt: operation.createdAt || Date.now() }
  const db = await openDb()
  if (!db) return value
  const transaction = db.transaction(OPERATIONS, 'readwrite')
  const done = transactionDone(transaction)
  await requestResult(transaction.objectStore(OPERATIONS).put(value))
  await done
  notifyQueueChanged()
  return value
}

export async function queueOfflineDiaryOperation(operation) {
  const db = await openDb()
  const accountScope = currentScope()
  const value = { ...operation, accountScope, kind: 'diary', id: operation.id || crypto.randomUUID(),
    createdAt: operation.createdAt || Date.now() }
  if (!db) return value

  const merged = await new Promise((resolve, reject) => {
    const transaction = db.transaction(OPERATIONS, 'readwrite')
    const target = transaction.objectStore(OPERATIONS)
    let mergeResult
    const request = target.index('accountScope').getAll(accountScope)
    request.onsuccess = () => {
      const all = request.result
      const previous = all.filter(item => item.kind === 'diary' && item.spaceId === value.spaceId
          && item.entityId === value.entityId).sort((left, right) => left.createdAt - right.createdAt).at(-1)
      mergeResult = mergeQueuedDiaryOperation(previous, value)
      if (mergeResult.action === 'replace') target.put({ ...mergeResult.operation, accountScope })
      else if (mergeResult.action === 'cancel-create') {
        all.filter(item => item.spaceId === value.spaceId
            && (item.entityId === value.entityId || item.diaryId === value.entityId))
          .forEach(item => target.delete(item.id))
      } else if (mergeResult.action === 'remove-previous') target.delete(previous.id)
      else target.put(value)
    }
    request.onerror = () => reject(request.error)
    transaction.oncomplete = () => resolve(mergeResult)
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
  notifyQueueChanged()
  return merged.operation || value
}

export async function listOfflineOperations(spaceId) {
  const accountScope = currentScope()
  const values = await readStore(OPERATIONS, target => target.index('accountScope').getAll(accountScope), [])
  return values.filter(value => !spaceId || value.spaceId === spaceId)
    .sort((left, right) => left.createdAt - right.createdAt)
}

export async function listQuarantinedOfflineOperations() {
  return readStore(QUARANTINE, target => target.getAll(), [])
}

export async function removeOfflineOperations(ids) {
  const db = await openDb()
  if (!db || !ids?.length) return
  const accountScope = currentScope()
  const existing = await readStore(OPERATIONS, target => target.index('accountScope').getAll(accountScope), [])
  const allowed = new Set(existing.map(item => item.id))
  const transaction = db.transaction(OPERATIONS, 'readwrite')
  const target = transaction.objectStore(OPERATIONS)
  ids.filter(id => allowed.has(id)).forEach(id => target.delete(id))
  await transactionDone(transaction)
  notifyQueueChanged()
}

function transactionDone(transaction) {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = resolve
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}

export async function pendingOfflineCount(spaceIds) {
  const values = await listOfflineOperations()
  if (!Array.isArray(spaceIds)) return values.length
  if (!spaceIds.length) return 0
  const allowedSpaces = new Set(spaceIds)
  return values.filter(value => allowedSpaces.has(value.spaceId)).length
}

export async function setOfflineMeta(key, value) {
  const accountScope = currentScope()
  await writeStore(META, target => target.put({ key: scopedKey(accountScope, key), rawKey: key, accountScope, value }))
}

export async function getOfflineMeta(key, fallback = null) {
  const accountScope = currentScope()
  const value = await readStore(META, target => target.get(scopedKey(accountScope, key)), null)
  return value?.value ?? fallback
}

export async function setOfflineCache(key, value) {
  const accountScope = currentScope()
  await writeStore(CACHE, target => target.put({ key: scopedKey(accountScope, key), rawKey: key,
    accountScope, value: stripEphemeralUrls(value), updatedAt: Date.now() }))
}

export async function getOfflineCache(key, maxAge = 7 * 24 * 60 * 60 * 1000) {
  const accountScope = currentScope()
  const value = await readStore(CACHE, target => target.get(scopedKey(accountScope, key)), null)
  if (!value || Date.now() - value.updatedAt > maxAge) return null
  return value.value
}

export async function clearOfflineSessionCache(accountScope = getAccountCacheScope()) {
  if (!accountScope || accountScope === 'anonymous') return
  const db = await openDb()
  if (!db) return
  const transaction = db.transaction([CACHE, META], 'readwrite')
  for (const name of [CACHE, META]) {
    const target = transaction.objectStore(name)
    const request = target.openCursor()
    request.onsuccess = () => {
      const cursor = request.result
      if (!cursor) return
      if (cursor.value.accountScope === accountScope) cursor.delete()
      cursor.continue()
    }
  }
  await transactionDone(transaction)
}

export async function clearOfflineData() {
  if (typeof indexedDB === 'undefined') return
  const db = await openDb().catch(() => null)
  db?.close()
  dbPromise = null
  await new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(DB_NAME)
    request.onsuccess = resolve
    request.onerror = () => reject(request.error)
    request.onblocked = resolve
  })
  notifyQueueChanged()
}

function scopedKey(scope, key) {
  return `${scope}|${key}`
}

function stripEphemeralUrls(value) {
  if (Array.isArray(value)) return value.map(stripEphemeralUrls)
  if (!value || typeof value !== 'object') return value
  const result = {}
  for (const [key, item] of Object.entries(value)) {
    if (['contentUrl', 'thumbnailUrl', 'posterUrl', 'waveformUrl', 'transcodedUrl', 'url', 'expiresAt',
      'mediaUrlExpiresAt'].includes(key)) continue
    result[key] = stripEphemeralUrls(item)
  }
  return result
}

async function readStore(name, operation, fallback) {
  const db = await openDb()
  if (!db) return fallback
  const transaction = db.transaction(name, 'readonly')
  return requestResult(operation(transaction.objectStore(name)))
}

async function writeStore(name, operation) {
  const db = await openDb()
  if (!db) return
  const transaction = db.transaction(name, 'readwrite')
  const done = transactionDone(transaction)
  await requestResult(operation(transaction.objectStore(name)))
  await done
}

function notifyQueueChanged() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('offline-queue:changed'))
}

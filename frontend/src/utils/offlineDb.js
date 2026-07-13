import { mergeQueuedDiaryOperation } from '@/utils/offlineQueue'

const DB_NAME = 'baby-diary-offline'
const DB_VERSION = 1
const OPERATIONS = 'operations'
const META = 'meta'
const CACHE = 'cache'

let dbPromise

function openDb() {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null)
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(OPERATIONS)) {
        const store = db.createObjectStore(OPERATIONS, { keyPath: 'id' })
        store.createIndex('spaceId', 'spaceId', { unique: false })
        store.createIndex('createdAt', 'createdAt', { unique: false })
      }
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META, { keyPath: 'key' })
      if (!db.objectStoreNames.contains(CACHE)) db.createObjectStore(CACHE, { keyPath: 'key' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return dbPromise
}

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

export async function queueOfflineOperation(operation) {
  const value = {
    ...operation,
    id: operation.id || crypto.randomUUID(),
    createdAt: operation.createdAt || Date.now()
  }
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
  const value = {
    ...operation,
    kind: 'diary',
    id: operation.id || crypto.randomUUID(),
    createdAt: operation.createdAt || Date.now()
  }
  if (!db) return value

  const merged = await new Promise((resolve, reject) => {
    const transaction = db.transaction(OPERATIONS, 'readwrite')
    const target = transaction.objectStore(OPERATIONS)
    let mergeResult
    const request = target.getAll()
    request.onsuccess = () => {
      const all = request.result
      const previous = all
        .filter(item => item.kind === 'diary' && item.spaceId === value.spaceId && item.entityId === value.entityId)
        .sort((left, right) => left.createdAt - right.createdAt)
        .at(-1)
      mergeResult = mergeQueuedDiaryOperation(previous, value)
      if (mergeResult.action === 'replace') {
        target.put(mergeResult.operation)
      } else if (mergeResult.action === 'cancel-create') {
        all.filter(item => item.spaceId === value.spaceId
            && (item.entityId === value.entityId || item.diaryId === value.entityId))
          .forEach(item => target.delete(item.id))
      } else if (mergeResult.action === 'remove-previous') {
        target.delete(previous.id)
      } else {
        target.put(value)
      }
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
  const values = await readStore(OPERATIONS, target => target.getAll(), [])
  return values
    .filter(value => !spaceId || value.spaceId === spaceId)
    .sort((left, right) => left.createdAt - right.createdAt)
}

export async function removeOfflineOperations(ids) {
  const db = await openDb()
  if (!db || !ids?.length) return
  const transaction = db.transaction(OPERATIONS, 'readwrite')
  const target = transaction.objectStore(OPERATIONS)
  ids.forEach(id => target.delete(id))
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
  if (!Array.isArray(spaceIds)) {
    return readStore(OPERATIONS, target => target.count(), 0)
  }
  if (!spaceIds.length) return 0

  const allowedSpaces = new Set(spaceIds)
  const values = await readStore(OPERATIONS, target => target.getAll(), [])
  return values.filter(value => allowedSpaces.has(value.spaceId)).length
}

export async function setOfflineMeta(key, value) {
  await writeStore(META, target => target.put({ key, value }))
}

export async function getOfflineMeta(key, fallback = null) {
  const value = await readStore(META, target => target.get(key), null)
  return value?.value ?? fallback
}

export async function setOfflineCache(key, value) {
  await writeStore(CACHE, target => target.put({ key, value, updatedAt: Date.now() }))
}

export async function getOfflineCache(key, maxAge = 7 * 24 * 60 * 60 * 1000) {
  const value = await readStore(CACHE, target => target.get(key), null)
  if (!value || Date.now() - value.updatedAt > maxAge) return null
  return value.value
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

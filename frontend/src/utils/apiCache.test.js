import test from 'node:test'
import assert from 'node:assert/strict'

import { cachedRequest, clearApiCache, invalidateApiCache } from './apiCache.js'

const memoryStorage = () => {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    clear: () => values.clear()
  }
}

test('cachedRequest deduplicates concurrent requests with the same key', async () => {
  clearApiCache()
  let calls = 0
  const loader = async () => {
    calls += 1
    return { ok: true, calls }
  }

  const [first, second] = await Promise.all([
    cachedRequest('same-key', loader, { ttl: 1000 }),
    cachedRequest('same-key', loader, { ttl: 1000 })
  ])

  assert.equal(calls, 1)
  assert.deepEqual(first, { ok: true, calls: 1 })
  assert.strictEqual(first, second)
})

test('cachedRequest reuses values inside ttl and reloads after invalidation', async () => {
  clearApiCache()
  let calls = 0
  const loader = async () => {
    calls += 1
    return { calls }
  }

  const first = await cachedRequest('ttl-key', loader, { ttl: 1000 })
  const cached = await cachedRequest('ttl-key', loader, { ttl: 1000 })
  invalidateApiCache('ttl-key')
  const refreshed = await cachedRequest('ttl-key', loader, { ttl: 1000 })

  assert.equal(calls, 2)
  assert.strictEqual(cached, first)
  assert.deepEqual(refreshed, { calls: 2 })
})

test('an invalidated in-flight request cannot restore stale cache data', async () => {
  clearApiCache()
  let resolveOld
  const oldRequest = cachedRequest('race-key', () => new Promise(resolve => {
    resolveOld = resolve
  }), { ttl: 1000 })
  await Promise.resolve()

  invalidateApiCache('race-key')
  const freshValue = await cachedRequest('race-key', async () => ({ version: 'fresh' }), { ttl: 1000 })
  resolveOld({ version: 'stale' })
  await oldRequest

  const cached = await cachedRequest('race-key', async () => {
    throw new Error('fresh response should still be cached')
  }, { ttl: 1000 })
  assert.deepEqual(freshValue, { version: 'fresh' })
  assert.deepEqual(cached, { version: 'fresh' })
})

test('an older request finishing does not remove the newer in-flight request', async () => {
  clearApiCache()
  let resolveOld
  let resolveFresh
  let calls = 0
  const oldRequest = cachedRequest('force-key', () => new Promise(resolve => {
    calls += 1
    resolveOld = resolve
  }))
  await Promise.resolve()

  const freshRequest = cachedRequest('force-key', () => new Promise(resolve => {
    calls += 1
    resolveFresh = resolve
  }), { force: true })
  await Promise.resolve()
  resolveOld('old')
  await oldRequest

  const deduplicated = cachedRequest('force-key', async () => {
    calls += 1
    return 'unexpected'
  })
  assert.strictEqual(deduplicated, freshRequest)
  resolveFresh('fresh')
  assert.equal(await deduplicated, 'fresh')
  assert.equal(calls, 2)
})

test('the same API cache key is isolated between authenticated accounts', async (t) => {
  const previousStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  const storage = memoryStorage()
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage })
  t.after(() => {
    clearApiCache()
    if (previousStorage) Object.defineProperty(globalThis, 'localStorage', previousStorage)
    else delete globalThis.localStorage
  })

  clearApiCache()
  let calls = 0
  storage.setItem('userInfo', JSON.stringify({ userId: 101, username: 'account-a' }))
  const accountA = await cachedRequest('anniversaries:list', async () => ({ owner: 'A', calls: ++calls }))

  storage.setItem('userInfo', JSON.stringify({ userId: 202, username: 'account-b' }))
  const accountB = await cachedRequest('anniversaries:list', async () => ({ owner: 'B', calls: ++calls }))

  storage.setItem('userInfo', JSON.stringify({ userId: 101, username: 'account-a' }))
  const accountAAgain = await cachedRequest('anniversaries:list', async () => {
    throw new Error('account A should reuse only its own cached value')
  })

  assert.deepEqual(accountA, { owner: 'A', calls: 1 })
  assert.deepEqual(accountB, { owner: 'B', calls: 2 })
  assert.strictEqual(accountAAgain, accountA)
})

import { getAccountCacheScope } from './sessionScope.js'

const cacheStore = new Map()
const inFlightStore = new Map()
const generationStore = new Map()
let globalGeneration = 0

const scopedKey = (key) => `${getAccountCacheScope()}::${key}`

export function stableStringify(value) {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`
  }
  return `{${Object.keys(value)
    .sort()
    .filter(key => value[key] !== undefined && value[key] !== null && value[key] !== '')
    .map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
    .join(',')}}`
}

export function cachedRequest(key, loader, options = {}) {
  const requestKey = scopedKey(key)
  const ttl = options.ttl ?? 30000
  const now = Date.now()
  const cached = cacheStore.get(requestKey)

  if (!options.force && cached && cached.expiresAt > now) {
    return Promise.resolve(cached.value)
  }

  if (!options.force && inFlightStore.has(requestKey)) {
    return inFlightStore.get(requestKey)
  }

  if (options.force) {
    generationStore.set(requestKey, (generationStore.get(requestKey) || 0) + 1)
    inFlightStore.delete(requestKey)
  }

  const requestGeneration = generationStore.get(requestKey) || 0
  const requestGlobalGeneration = globalGeneration

  const request = Promise.resolve()
    .then(loader)
    .then(value => {
      if (requestGlobalGeneration === globalGeneration && requestGeneration === (generationStore.get(requestKey) || 0)) {
        cacheStore.set(requestKey, {
          value,
          expiresAt: Date.now() + ttl
        })
      }
      return value
    })
    .finally(() => {
      if (inFlightStore.get(requestKey) === request) {
        inFlightStore.delete(requestKey)
      }
    })

  inFlightStore.set(requestKey, request)
  return request
}

export function invalidateApiCache(match) {
  if (!match) {
    clearApiCache()
    return
  }

  const matchKey = scopedKey(match)
  const knownKeys = new Set([
    ...cacheStore.keys(),
    ...inFlightStore.keys(),
    ...generationStore.keys()
  ])
  for (const key of knownKeys) {
    if (key === matchKey || key.startsWith(matchKey)) {
      generationStore.set(key, (generationStore.get(key) || 0) + 1)
      cacheStore.delete(key)
      inFlightStore.delete(key)
    }
  }
}

export function clearApiCache() {
  globalGeneration += 1
  cacheStore.clear()
  inFlightStore.clear()
  generationStore.clear()
}

const cacheStore = new Map()
const inFlightStore = new Map()
const generationStore = new Map()
let globalGeneration = 0

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
  const ttl = options.ttl ?? 30000
  const now = Date.now()
  const cached = cacheStore.get(key)

  if (!options.force && cached && cached.expiresAt > now) {
    return Promise.resolve(cached.value)
  }

  if (!options.force && inFlightStore.has(key)) {
    return inFlightStore.get(key)
  }

  if (options.force) {
    generationStore.set(key, (generationStore.get(key) || 0) + 1)
    inFlightStore.delete(key)
  }

  const requestGeneration = generationStore.get(key) || 0
  const requestGlobalGeneration = globalGeneration

  const request = Promise.resolve()
    .then(loader)
    .then(value => {
      if (requestGlobalGeneration === globalGeneration && requestGeneration === (generationStore.get(key) || 0)) {
        cacheStore.set(key, {
          value,
          expiresAt: Date.now() + ttl
        })
      }
      return value
    })
    .finally(() => {
      if (inFlightStore.get(key) === request) {
        inFlightStore.delete(key)
      }
    })

  inFlightStore.set(key, request)
  return request
}

export function invalidateApiCache(match) {
  if (!match) {
    clearApiCache()
    return
  }

  const knownKeys = new Set([
    ...cacheStore.keys(),
    ...inFlightStore.keys(),
    ...generationStore.keys()
  ])
  for (const key of knownKeys) {
    if (key === match || key.startsWith(match)) {
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

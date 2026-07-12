import { clearApiCache } from './apiCache.js'
import { advanceClientSessionGeneration } from './sessionScope.js'

const removeStorageItem = (storage, key) => {
  try {
    storage?.removeItem(key)
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}

export function resetClientSession(reason = 'session-reset') {
  const generation = advanceClientSessionGeneration()
  clearApiCache()

  if (typeof localStorage !== 'undefined') {
    removeStorageItem(localStorage, 'activeSpaceId')
  }
  if (typeof sessionStorage !== 'undefined') {
    removeStorageItem(sessionStorage, 'stepUpToken')
    removeStorageItem(sessionStorage, 'stepUpExpiresAt')
  }

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('auth:session-reset', {
      detail: { generation, reason }
    }))
  }

  return generation
}

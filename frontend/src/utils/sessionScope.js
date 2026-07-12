let clientSessionGeneration = 0

const readLocalStorage = (key) => {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(key)
  } catch {
    return null
  }
}

const readStoredUser = () => {
  try {
    return JSON.parse(readLocalStorage('userInfo') || 'null')
  } catch {
    return null
  }
}

export function getAccountCacheScope() {
  const user = readStoredUser()
  const userId = user?.userId ?? user?.id
  if (userId !== undefined && userId !== null && userId !== '') {
    return `user:${String(userId)}`
  }
  if (user?.username) {
    return `username:${String(user.username)}`
  }

  const token = readLocalStorage('token')
  return token ? `token:${token}` : 'anonymous'
}

export function getClientSessionGeneration() {
  return clientSessionGeneration
}

export function advanceClientSessionGeneration() {
  clientSessionGeneration += 1
  return clientSessionGeneration
}

export function isClientSessionGenerationCurrent(generation) {
  return generation === clientSessionGeneration
}

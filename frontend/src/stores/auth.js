import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import router from '@/router'
import { resetClientSession } from '@/utils/sessionBoundary'
import { getClientSessionGeneration } from '@/utils/sessionScope'
import { getAccountCacheScope } from '@/utils/sessionScope'
import { clearOfflineSessionCache } from '@/utils/offlineDb'
import { useWorkspaceStore } from '@/stores/workspace'

const persistedUserInfo = user => user ? {
  ...user,
  avatarMedia: user.avatarMedia ? { id: user.avatarMedia.id } : null
} : null

const persistUserInfo = user => localStorage.setItem('userInfo', JSON.stringify(persistedUserInfo(user)))

const readUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')
  } catch {
    localStorage.removeItem('userInfo')
    return null
  }
}

const userIdentity = (user) => user?.id ?? user?.username ?? null

export const useAuthStore = defineStore('auth', () => {
  const USER_INFO_TTL = 30000
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(readUserInfo())
  const sessionVersion = ref(getClientSessionGeneration())
  let userInfoRequest = null
  let logoutRequest = null
  let lastUserInfoFetchAt = 0

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')

  async function login(loginData) {
    try {
      if (logoutRequest) await logoutRequest
      const { authApi } = await import('@/api/auth')
      const session = await authApi.login(loginData)
      sessionVersion.value = resetClientSession('login')
      token.value = session.token
      userInfo.value = session.userInfo
      persistUserInfo(session.userInfo)
      localStorage.setItem('token', session.token)
      lastUserInfoFetchAt = Date.now()
      return { success: true }
    } catch (error) {
      return { success: false, message: error.message || '登录失败' }
    }
  }

  async function register(registerData) {
    try {
      const { authApi } = await import('@/api/auth')
      await authApi.register(registerData)
      return { success: true, message: '注册成功' }
    } catch (error) {
      return { success: false, message: error.message || '注册失败' }
    }
  }

  function clearAuth(reason = 'session-cleared') {
    const previousScope = getAccountCacheScope()
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    clearOfflineSessionCache(previousScope).catch(() => {})
    userInfoRequest = null
    lastUserInfoFetchAt = 0
    sessionVersion.value = resetClientSession(reason)
  }

  async function logout() {
    const accessToken = token.value
    clearAuth('logout')
    const navigation = router.replace('/login')
    const pendingLogout = (async () => {
      try {
        const { authApi } = await import('@/api/auth')
        await authApi.logout(accessToken)
      } catch {
        // Local logout still proceeds when the server cannot be reached.
      }
    })()
    logoutRequest = pendingLogout
    try {
      await Promise.all([navigation, pendingLogout])
    } finally {
      if (logoutRequest === pendingLogout) {
        logoutRequest = null
      }
    }
  }

  async function getUserInfo(options = {}) {
    if (!token.value) return null
    if (!options.force && userInfo.value && Date.now() - lastUserInfoFetchAt < USER_INFO_TTL) {
      return userInfo.value
    }
    if (userInfoRequest) return userInfoRequest

    const requestedToken = token.value
    const request = (async () => {
      try {
        const { authApi } = await import('@/api/auth')
        const profile = await authApi.getUserInfo()
        if (token.value === requestedToken) {
          userInfo.value = profile
          persistUserInfo(profile)
          lastUserInfoFetchAt = Date.now()
          return profile
        }
        return null
      } catch {
        return null
      }
    })()

    userInfoRequest = request
    try {
      return await request
    } finally {
      if (userInfoRequest === request) {
        userInfoRequest = null
      }
    }
  }

  async function uploadAvatar(file) {
    const { authApi } = await import('@/api/auth')
    const workspaceStore = useWorkspaceStore()
    await workspaceStore.loadSpaces()
    if (!workspaceStore.activeSpaceId) throw new Error('当前账户没有可用日记空间')
    const profile = await authApi.uploadAvatar(workspaceStore.activeSpaceId, file)
    userInfo.value = profile
    persistUserInfo(profile)
    lastUserInfoFetchAt = Date.now()
    return profile
  }

  async function changePassword(payload) {
    const { authApi } = await import('@/api/auth')
    const response = await authApi.changePassword(payload)
    return response
  }

  function syncAuthFromStorage(event) {
    if (!['token', 'userInfo'].includes(event.key)) return

    const storedToken = localStorage.getItem('token') || ''
    const storedUser = readUserInfo()
    const accountChanged = userIdentity(userInfo.value) !== userIdentity(storedUser)
    const sessionEnded = !storedToken && !!token.value

    if (accountChanged || sessionEnded) {
      sessionVersion.value = resetClientSession('storage')
    }
    token.value = storedToken
    userInfo.value = storedUser
    userInfoRequest = null
    lastUserInfoFetchAt = storedUser ? Date.now() : 0

    if (!storedToken && router.currentRoute.value.meta.requiresAuth) {
      router.replace('/login')
    } else if (storedToken && ['/login', '/register'].includes(router.currentRoute.value.path)) {
      router.replace('/')
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:expired', () => clearAuth('expired'))
    window.addEventListener('storage', syncAuthFromStorage)
    window.addEventListener('auth:refreshed', event => {
      token.value = event.detail?.token || localStorage.getItem('token') || ''
      userInfo.value = event.detail?.userInfo || readUserInfo()
      lastUserInfoFetchAt = Date.now()
    })
  }

  return {
    token,
    userInfo,
    sessionVersion,
    isLoggedIn,
    username,
    login,
    register,
    clearAuth,
    logout,
    getUserInfo,
    uploadAvatar,
    changePassword
  }
})

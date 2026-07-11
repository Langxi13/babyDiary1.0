import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import router from '@/router'

const readUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')
  } catch {
    localStorage.removeItem('userInfo')
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const USER_INFO_TTL = 30000
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(readUserInfo())
  let userInfoRequest = null
  let lastUserInfoFetchAt = 0

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')

  async function login(loginData) {
    try {
      const { authApi } = await import('@/api/auth')
      const response = await authApi.login(loginData)
      if (response.code === 200) {
        token.value = response.data.token
        userInfo.value = response.data.userInfo
        localStorage.setItem('token', response.data.token)
        localStorage.setItem('userInfo', JSON.stringify(response.data.userInfo))
        lastUserInfoFetchAt = Date.now()
        return { success: true }
      }
      return { success: false, message: response.message }
    } catch (error) {
      return { success: false, message: error.message || '登录失败' }
    }
  }

  async function register(registerData) {
    try {
      const { authApi } = await import('@/api/auth')
      const response = await authApi.register(registerData)
      if (response.code === 200) {
        return { success: true, message: '注册成功' }
      }
      return { success: false, message: response.message }
    } catch (error) {
      return { success: false, message: error.message || '注册失败' }
    }
  }

  function clearAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    lastUserInfoFetchAt = 0
  }

  async function logout() {
    try {
      const { authApi } = await import('@/api/auth')
      await authApi.logout()
    } catch {
      // Local logout still proceeds when the server cannot be reached.
    }
    clearAuth()
    router.push('/login')
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
        const response = await authApi.getUserInfo()
        if (response.code === 200 && token.value === requestedToken) {
          userInfo.value = response.data
          localStorage.setItem('userInfo', JSON.stringify(response.data))
          lastUserInfoFetchAt = Date.now()
          return response.data
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
    const formData = new FormData()
    formData.append('avatarFile', file)
    const response = await authApi.uploadAvatar(formData)
    if (response.code === 200) {
      userInfo.value = response.data
      localStorage.setItem('userInfo', JSON.stringify(response.data))
      lastUserInfoFetchAt = Date.now()
    }
    return response
  }

  async function changePassword(payload) {
    const { authApi } = await import('@/api/auth')
    const response = await authApi.changePassword(payload)
    return response
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:expired', clearAuth)
    window.addEventListener('auth:refreshed', event => {
      token.value = event.detail?.token || localStorage.getItem('token') || ''
      userInfo.value = event.detail?.userInfo || readUserInfo()
      lastUserInfoFetchAt = Date.now()
    })
  }

  return {
    token,
    userInfo,
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

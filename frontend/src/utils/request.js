import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import router from '@/router'
import {
  getClientSessionGeneration,
  isClientSessionGenerationCurrent
} from '@/utils/sessionScope'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true
})

let refreshPromise = null
let refreshGeneration = null
let refreshAbortController = null

const staleSessionError = (config) => new axios.CanceledError('请求所属会话已结束', config)

const isStaleSessionRequest = (config) => {
  const generation = config?.__clientSessionGeneration
  return generation !== undefined && !isClientSessionGenerationCurrent(generation)
}

const cancelRefreshRequest = () => {
  refreshAbortController?.abort()
  refreshAbortController = null
  refreshPromise = null
  refreshGeneration = null
}

const getRefreshRequest = () => {
  const generation = getClientSessionGeneration()
  if (!refreshPromise || refreshGeneration !== generation) {
    cancelRefreshRequest()
    const controller = new AbortController()
    const pending = axios.post(
      `${request.defaults.baseURL || ''}/api/v2/auth/refresh`,
      null,
      { withCredentials: true, timeout: 15000, signal: controller.signal }
    ).finally(() => {
      if (refreshPromise === pending) {
        refreshPromise = null
        refreshGeneration = null
        refreshAbortController = null
      }
    })
    refreshPromise = pending
    refreshGeneration = generation
    refreshAbortController = controller
  }
  return { generation, promise: refreshPromise }
}

if (typeof window !== 'undefined') {
  window.addEventListener('auth:session-reset', cancelRefreshRequest)
}

const parseMessage = async (data, fallback = '服务器错误') => {
  if (data instanceof Blob) {
    const text = await data.text()
    if (!text) return fallback
    try {
      const payload = JSON.parse(text)
      return payload.message || payload.detail || fallback
    } catch {
      return text || fallback
    }
  }

  if (typeof data === 'string') {
    try {
      const payload = JSON.parse(data)
      return payload.message || payload.detail || data || fallback
    } catch {
      return data || fallback
    }
  }

  return data?.message || data?.detail || fallback
}

const redirectToLogin = () => {
  const currentRoute = router.currentRoute.value
  if (currentRoute.path === '/login') return

  router.push({
    path: '/login',
    query: currentRoute.fullPath ? { redirect: currentRoute.fullPath } : {}
  })
}

request.interceptors.request.use(
  config => {
    config.__clientSessionGeneration = getClientSessionGeneration()
    const token = localStorage.getItem('token')
    if (token && !config.headers.Authorization) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  async response => {
    if (isStaleSessionRequest(response.config)) {
      return Promise.reject(staleSessionError(response.config))
    }
    if (response.config.responseType === 'blob') {
      const contentType = response.headers?.['content-type'] || ''
      if (contentType.includes('json')) {
        const message = await parseMessage(response.data, '请求失败')
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }
      return response.data
    }
    const { data } = response
    if (data?.code === 200) {
      return data
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  async error => {
    if (axios.isCancel(error) || isStaleSessionRequest(error.config)) {
      return Promise.reject(axios.isCancel(error) ? error : staleSessionError(error.config))
    }
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        const originalRequest = error.config || {}
        if (originalRequest.__skipAuthRecovery) {
          return Promise.reject(error)
        }
        const isRefreshRequest = originalRequest.url?.includes('/api/v2/auth/refresh')
        const isPublicAuthRequest = /\/api\/v2\/auth\/(login|password|email\/confirm)/.test(originalRequest.url || '')
        const isPublicShareRequest = originalRequest.url?.includes('/api/v2/public/shares/')
        if (isPublicShareRequest) {
          ElMessage.error(await parseMessage(data, '访问密码不正确'))
          return Promise.reject(error)
        }
        if (!originalRequest.__retried && !isRefreshRequest && !isPublicAuthRequest && localStorage.getItem('token')) {
          originalRequest.__retried = true
          try {
            const refreshRequest = getRefreshRequest()
            const refreshResponse = await refreshRequest.promise
            if (!isClientSessionGenerationCurrent(refreshRequest.generation)) {
              return Promise.reject(staleSessionError(originalRequest))
            }
            const payload = refreshResponse.data
            if (payload?.code === 200 && payload.data?.token) {
              localStorage.setItem('userInfo', JSON.stringify(payload.data.userInfo || null))
              localStorage.setItem('token', payload.data.token)
              window.dispatchEvent(new CustomEvent('auth:refreshed', { detail: payload.data }))
              originalRequest.headers = originalRequest.headers || {}
              originalRequest.headers.Authorization = `Bearer ${payload.data.token}`
              return request(originalRequest)
            }
          } catch {
            // The shared expiry path below clears stale local credentials.
          }
        }
        if (isStaleSessionRequest(originalRequest)) {
          return Promise.reject(staleSessionError(originalRequest))
        }
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.dispatchEvent(new Event('auth:expired'))
        redirectToLogin()
      } else if (status === 403) {
        ElMessage.error(await parseMessage(data, '没有权限访问'))
      } else if (status === 409) {
        ElMessage.warning(await parseMessage(data, '内容已被其他成员更新，请刷新后重试'))
      } else if (status === 423) {
        ElMessage.warning(await parseMessage(data, '请先完成二次验证'))
      } else if (status === 429) {
        ElMessage.warning(await parseMessage(data, '请求过于频繁，请稍后再试'))
      } else if (status === 404) {
        ElMessage.error(await parseMessage(data, '资源不存在'))
      } else {
        ElMessage.error(await parseMessage(data, '服务器错误'))
      }
    } else {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(error)
  }
)

export default request

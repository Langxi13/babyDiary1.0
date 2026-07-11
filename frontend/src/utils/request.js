import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import router from '@/router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true
})

let refreshPromise = null

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
    const token = localStorage.getItem('token')
    if (token) {
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
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        const originalRequest = error.config || {}
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
            if (!refreshPromise) {
              refreshPromise = axios.post(
                `${request.defaults.baseURL || ''}/api/v2/auth/refresh`,
                null,
                { withCredentials: true, timeout: 15000 }
              ).finally(() => {
                refreshPromise = null
              })
            }
            const refreshResponse = await refreshPromise
            const payload = refreshResponse.data
            if (payload?.code === 200 && payload.data?.token) {
              localStorage.setItem('token', payload.data.token)
              localStorage.setItem('userInfo', JSON.stringify(payload.data.userInfo || null))
              window.dispatchEvent(new CustomEvent('auth:refreshed', { detail: payload.data }))
              originalRequest.headers = originalRequest.headers || {}
              originalRequest.headers.Authorization = `Bearer ${payload.data.token}`
              return request(originalRequest)
            }
          } catch {
            // The shared expiry path below clears stale local credentials.
          }
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

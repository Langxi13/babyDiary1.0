import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import router from '@/router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
})

const parseMessage = async (data, fallback = '服务器错误') => {
  if (data instanceof Blob) {
    const text = await data.text()
    if (!text) return fallback
    try {
      const payload = JSON.parse(text)
      return payload.message || fallback
    } catch {
      return text || fallback
    }
  }

  if (typeof data === 'string') {
    try {
      const payload = JSON.parse(data)
      return payload.message || data || fallback
    } catch {
      return data || fallback
    }
  }

  return data?.message || fallback
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
      if (contentType.includes('application/json')) {
        const message = await parseMessage(response.data, '请求失败')
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }
      return response.data
    }
    const { data } = response
    if (data.code === 200) {
      return data
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  async error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.dispatchEvent(new Event('auth:expired'))
        redirectToLogin()
      } else if (status === 403) {
        ElMessage.error('没有权限访问')
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

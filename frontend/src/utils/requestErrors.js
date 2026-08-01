import axios from 'axios'

const NETWORK_NOTICE_WINDOW_MS = 3000
let lastNotice = { key: '', at: 0 }

export const requestErrorCategory = error => {
  if (axios.isCancel(error) || error?.name === 'AbortError' || error?.code === 'ERR_CANCELED') return 'cancelled'
  if (error?.code === axios.AxiosError.ECONNABORTED || error?.code === 'ETIMEDOUT') return 'timeout'
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return 'offline'
  return 'unreachable'
}

export const requestErrorMessage = category => ({
  offline: '当前设备已离线，请检查网络连接',
  timeout: '服务器响应超时，请稍后重试',
  unreachable: '无法连接服务器，请检查网络或服务器地址'
})[category] || '请求失败'

export const shouldNotifyRequestError = (category, now = Date.now()) => {
  if (category === 'cancelled') return false
  if (lastNotice.key === category && now - lastNotice.at < NETWORK_NOTICE_WINDOW_MS) return false
  lastNotice = { key: category, at: now }
  return true
}

const routeTemplate = value => {
  const raw = String(value || '').split('?', 1)[0]
  let path = raw
  try {
    path = new URL(raw, 'https://example.com').pathname
  } catch {
    // Keep malformed relative paths useful without exposing query values.
  }
  return path.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, ':id')
}

export const emitRequestDiagnostic = (error, category) => {
  if (typeof window === 'undefined') return
  const config = error?.config || {}
  window.dispatchEvent(new CustomEvent('app:request-diagnostic', {
    detail: {
      category,
      method: String(config.method || 'GET').toUpperCase(),
      path: routeTemplate(config.url),
      elapsedMs: config.__startedAt ? Math.max(0, Date.now() - config.__startedAt) : null
    }
  }))
}

export const resetRequestErrorNotices = () => {
  lastNotice = { key: '', at: 0 }
}

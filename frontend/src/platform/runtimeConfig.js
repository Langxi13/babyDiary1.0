import { Capacitor, CapacitorHttp } from '@capacitor/core'
import { Preferences } from '@capacitor/preferences'

const SERVER_ORIGIN_KEY = 'babyDiaryServerOrigin'
const HTTP_DEBUG_HOSTS = new Set(['localhost', '127.0.0.1', '10.0.2.2'])
const runtimeEnv = import.meta.env ?? {}
const RUNTIME_ALLOWS_DEBUG_HTTP = runtimeEnv.DEV || runtimeEnv.VITE_NATIVE_ALLOW_HTTP === 'true'

let serverOrigin = ''
let initialized = false

export const isNativeApp = () => Capacitor.isNativePlatform()

export const normalizeServerOrigin = (value, options = {}) => {
  const raw = String(value || '').trim()
  if (!raw) throw new Error('请输入服务器地址')

  let url
  try {
    url = new URL(raw)
  } catch {
    throw new Error('服务器地址格式不正确')
  }

  const allowDebugHttp = options.allowDebugHttp ?? RUNTIME_ALLOWS_DEBUG_HTTP
  const permittedHttp = allowDebugHttp
    && url.protocol === 'http:'
    && (HTTP_DEBUG_HOSTS.has(url.hostname) || /^192\.168\.|^10\.|^172\.(1[6-9]|2\d|3[01])\./.test(url.hostname))

  if (url.protocol !== 'https:' && !permittedHttp) {
    throw new Error('服务器必须使用 HTTPS')
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('服务器地址不能包含账号、查询参数或片段')
  }
  if (url.pathname !== '/' && url.pathname !== '') {
    throw new Error('当前仅支持部署在域名根路径的服务器')
  }

  return url.origin
}

export const initializeRuntimeConfig = async () => {
  if (initialized) return serverOrigin
  initialized = true
  if (!isNativeApp()) return serverOrigin

  try {
    const stored = await Preferences.get({ key: SERVER_ORIGIN_KEY })
    if (!stored.value) return serverOrigin
    serverOrigin = normalizeServerOrigin(stored.value)
  } catch {
    serverOrigin = ''
    await Preferences.remove({ key: SERVER_ORIGIN_KEY }).catch(() => {})
  }
  return serverOrigin
}

export const getServerOrigin = () => serverOrigin
export const hasServerOrigin = () => !isNativeApp() || !!serverOrigin

export const resolveServerUrl = (path = '') => {
  const value = String(path || '')
  if (/^(https?:|blob:|data:|capacitor:)/i.test(value)) return value
  if (!isNativeApp()) return value
  if (!serverOrigin) return value
  return new URL(value.startsWith('/') ? value : `/${value}`, serverOrigin).toString()
}

export const testServerConnection = async (value) => {
  const origin = normalizeServerOrigin(value)
  let response
  try {
    response = await CapacitorHttp.get({
      url: `${origin}/api/v2/client/bootstrap`,
      connectTimeout: 10000,
      readTimeout: 10000,
      headers: { Accept: 'application/json' }
    })
  } catch {
    throw new Error('无法连接服务器，请检查地址、HTTPS 证书和网络')
  }
  let payload
  try {
    payload = typeof response.data === 'string' ? JSON.parse(response.data) : response.data
  } catch {
    throw new Error('该地址没有返回兼容的 Baby Diary 服务信息')
  }
  if (response.status !== 200 || payload?.code !== 200 || payload?.data?.apiVersion !== 2) {
    throw new Error('该地址不是兼容的 Baby Diary 服务')
  }
  if (payload.data.nativeSessionMode !== 'COOKIE') {
    throw new Error('服务器暂不支持当前原生会话方式')
  }
  return { origin, capabilities: payload.data }
}

export const saveServerOrigin = async (value) => {
  const normalized = normalizeServerOrigin(value)
  await Preferences.set({ key: SERVER_ORIGIN_KEY, value: normalized })
  serverOrigin = normalized
  window.dispatchEvent(new CustomEvent('native:server-changed', { detail: { origin: normalized } }))
  return normalized
}

export const clearServerOrigin = async () => {
  await Preferences.remove({ key: SERVER_ORIGIN_KEY })
  serverOrigin = ''
}

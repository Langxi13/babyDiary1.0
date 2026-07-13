import { Capacitor, CapacitorHttp } from '@capacitor/core'
import { getServerOrigin, isNativeApp } from '@/platform/runtimeConfig'

const parsePayload = (data) => {
  if (typeof data !== 'string') return data
  try {
    return JSON.parse(data)
  } catch {
    return null
  }
}

const requestError = (response, payload) => {
  const error = new Error(payload?.message || payload?.detail || `请求失败（${response.status}）`)
  error.response = { status: response.status, data: payload }
  return error
}

export const nativeAuthRawRequest = async (method, path, data, headers = {}) => {
  if (!isNativeApp()) throw new Error('当前不在原生应用中')
  const origin = getServerOrigin()
  if (!origin) throw new Error('请先配置服务器地址')

  const response = await CapacitorHttp.request({
    method,
    url: `${origin}${path}`,
    data,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Device-Name': `Baby Diary ${Capacitor.getPlatform()}`,
      ...headers
    },
    connectTimeout: 15000,
    readTimeout: 30000
  })
  const payload = parsePayload(response.data)
  if (response.status < 200 || response.status >= 300 || payload?.code !== 200) {
    throw requestError(response, payload)
  }
  return { ...response, data: payload }
}

export const nativeAuthResultRequest = async (method, path, data, headers = {}) => {
  const response = await nativeAuthRawRequest(method, path, data, headers)
  return response.data
}

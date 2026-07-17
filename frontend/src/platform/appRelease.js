import { App } from '@capacitor/app'
import { Browser } from '@capacitor/browser'
import { Capacitor } from '@capacitor/core'
import { getServerOrigin, isNativeApp, resolveServerUrl } from '@/platform/runtimeConfig'

const WEB_APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : '1.0.0'

const positiveInteger = value => {
  const parsed = Number.parseInt(String(value || ''), 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
}

export const getCurrentClientInfo = async () => {
  const platform = Capacitor.getPlatform()
  if (!isNativeApp()) {
    return {
      name: 'Baby Diary',
      id: 'web',
      version: WEB_APP_VERSION,
      build: 0,
      platform
    }
  }

  const info = await App.getInfo()
  return {
    name: info.name || 'Baby Diary',
    id: info.id || '',
    version: info.version || 'unknown',
    build: positiveInteger(info.build),
    platform
  }
}

export const evaluateAndroidUpdate = (clientInfo, manifest) => {
  const currentVersionCode = positiveInteger(clientInfo?.build)
  const latestVersionCode = positiveInteger(manifest?.latestVersionCode)
  const minimumVersionCode = positiveInteger(manifest?.minimumVersionCode) || 1
  const supported = clientInfo?.platform === 'android' && manifest?.enabled === true
  const available = supported && currentVersionCode > 0 && latestVersionCode > currentVersionCode

  return {
    supported,
    available,
    required: available && (manifest?.mandatory === true || currentVersionCode < minimumVersionCode),
    currentVersionCode,
    latestVersionCode,
    minimumVersionCode
  }
}

export const trustedUpdateUrl = value => {
  const raw = String(value || '').trim()
  if (!raw) throw new Error('服务器没有提供更新地址')

  const base = isNativeApp() ? getServerOrigin() : window.location.origin
  const resolved = resolveServerUrl(raw)
  const url = new URL(resolved, base || undefined)
  if (url.protocol !== 'https:' || url.username || url.password || url.hash) {
    throw new Error('更新地址必须使用 HTTPS')
  }
  return url.toString()
}

export const openUpdateDownload = async value => {
  const url = trustedUpdateUrl(value)
  if (isNativeApp()) {
    await Browser.open({ url })
    return url
  }

  const opened = window.open(url, '_blank', 'noopener,noreferrer')
  if (!opened) throw new Error('浏览器阻止了下载窗口，请允许弹出窗口后重试')
  return url
}

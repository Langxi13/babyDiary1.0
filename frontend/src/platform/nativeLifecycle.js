import { App } from '@capacitor/app'
import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core'
import { Network } from '@capacitor/network'
import { isNativeApp } from '@/platform/runtimeConfig'

const emitNetworkStatus = status => {
  window.dispatchEvent(new CustomEvent('native:network-status', {
    detail: {
      connected: status?.connected !== false,
      connectionType: status?.connectionType || 'unknown'
    }
  }))
}

const closeVisibleOverlay = () => {
  const overlays = [...document.querySelectorAll('.el-overlay')]
  const visible = overlays.some(element => {
    const style = window.getComputedStyle(element)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
  if (!visible) return false
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
  return true
}

export const startNativeLifecycle = async ({ router, onResume, onNetworkChange }) => {
  if (!isNativeApp()) return async () => {}

  await SystemBars.setStyle({ style: SystemBarsStyle.Light }).catch(() => {})
  const handles = []
  handles.push(await Network.addListener('networkStatusChange', status => {
    emitNetworkStatus(status)
    onNetworkChange?.(status)
  }))
  handles.push(await App.addListener('appStateChange', state => {
    if (state.isActive) onResume?.()
  }))
  handles.push(await App.addListener('backButton', async () => {
    const backEvent = new CustomEvent('native:back', { cancelable: true })
    if (!window.dispatchEvent(backEvent)) return
    if (closeVisibleOverlay()) return

    const path = router.currentRoute.value.path
    if (path === '/') {
      await App.minimizeApp()
    } else if (['/diaries', '/album', '/profile', '/spaces'].includes(path)) {
      await router.replace('/')
    } else if (path === '/login' || path === '/connect-server') {
      await App.minimizeApp()
    } else {
      router.back()
    }
  }))

  const initialStatus = await Network.getStatus().catch(() => ({ connected: navigator.onLine }))
  emitNetworkStatus(initialStatus)
  onNetworkChange?.(initialStatus)

  return async () => {
    await Promise.allSettled(handles.map(handle => handle.remove()))
  }
}

export const nativePlatformClass = () => isNativeApp()
  ? `native-app platform-${Capacitor.getPlatform()}`
  : ''

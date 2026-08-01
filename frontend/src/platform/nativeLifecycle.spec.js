import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  appAddListener: vi.fn(),
  networkAddListener: vi.fn(),
  getStatus: vi.fn(),
  minimizeApp: vi.fn(),
  setStyle: vi.fn(),
  remove: vi.fn()
}))

vi.mock('@capacitor/app', () => ({
  App: {
    addListener: mocks.appAddListener,
    minimizeApp: mocks.minimizeApp
  }
}))
vi.mock('@capacitor/network', () => ({
  Network: {
    addListener: mocks.networkAddListener,
    getStatus: mocks.getStatus
  }
}))
vi.mock('@capacitor/core', () => ({
  Capacitor: { getPlatform: () => 'android' },
  SystemBars: { setStyle: mocks.setStyle },
  SystemBarsStyle: { Light: 'LIGHT' }
}))
vi.mock('@/platform/runtimeConfig', () => ({ isNativeApp: () => true }))

import { nativePlatformClass, startNativeLifecycle } from './nativeLifecycle'

describe('native application lifecycle', () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset())
    mocks.remove.mockResolvedValue(undefined)
    mocks.setStyle.mockResolvedValue(undefined)
    mocks.networkAddListener.mockResolvedValue({ remove: mocks.remove })
    mocks.appAddListener.mockResolvedValue({ remove: mocks.remove })
    mocks.getStatus.mockResolvedValue({ connected: true, connectionType: 'wifi' })
    mocks.minimizeApp.mockResolvedValue(undefined)
  })

  it('forwards network and foreground events and removes native listeners', async () => {
    const callbacks = {}
    mocks.networkAddListener.mockImplementation(async (name, callback) => {
      callbacks[name] = callback
      return { remove: mocks.remove }
    })
    mocks.appAddListener.mockImplementation(async (name, callback) => {
      callbacks[name] = callback
      return { remove: mocks.remove }
    })
    const onResume = vi.fn()
    const onNetworkChange = vi.fn()
    const router = {
      currentRoute: { value: { path: '/' } },
      replace: vi.fn(),
      back: vi.fn()
    }

    const stop = await startNativeLifecycle({ router, onResume, onNetworkChange })
    callbacks.networkStatusChange({ connected: false, connectionType: 'none' })
    callbacks.appStateChange({ isActive: true })
    await callbacks.backButton()

    expect(onNetworkChange).toHaveBeenNthCalledWith(1, {
      connected: true,
      connectionType: 'wifi'
    })
    expect(onNetworkChange).toHaveBeenNthCalledWith(2, {
      connected: false,
      connectionType: 'none'
    })
    expect(onResume).toHaveBeenCalledOnce()
    expect(mocks.minimizeApp).toHaveBeenCalledOnce()
    expect(nativePlatformClass()).toBe('native-app platform-android')

    await stop()
    expect(mocks.remove).toHaveBeenCalledTimes(3)
  })
})

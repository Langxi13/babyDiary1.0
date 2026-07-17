import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAppUpdateStore } from './appUpdate'

const mocks = vi.hoisted(() => ({
  bootstrap: vi.fn(),
  getCurrentClientInfo: vi.fn(),
  openUpdateDownload: vi.fn()
}))

vi.mock('@/api/client', () => ({
  clientApi: { bootstrap: mocks.bootstrap }
}))

vi.mock('@/platform/appRelease', () => ({
  evaluateAndroidUpdate: (client, manifest) => ({
    available: client?.platform === 'android' && manifest?.latestVersionCode > client?.build,
    required: false
  }),
  getCurrentClientInfo: mocks.getCurrentClientInfo,
  openUpdateDownload: mocks.openUpdateDownload
}))

describe('app update store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.bootstrap.mockReset()
    mocks.getCurrentClientInfo.mockReset()
    mocks.openUpdateDownload.mockReset()
  })

  it('loads current client and server release metadata once', async () => {
    mocks.getCurrentClientInfo.mockResolvedValue({ platform: 'android', build: 1, version: 'beta.1' })
    mocks.bootstrap.mockResolvedValue({ data: { androidUpdate: { enabled: true, latestVersionCode: 2 } } })
    const store = useAppUpdateStore()

    await store.check()

    expect(store.checked).toBe(true)
    expect(store.updateAvailable).toBe(true)
    expect(mocks.bootstrap).toHaveBeenCalledOnce()
  })

  it('discards an old server response after the runtime server changes', async () => {
    let resolveBootstrap
    mocks.getCurrentClientInfo.mockResolvedValue({ platform: 'android', build: 1, version: 'beta.1' })
    mocks.bootstrap.mockReturnValue(new Promise(resolve => { resolveBootstrap = resolve }))
    const store = useAppUpdateStore()

    const pending = store.check()
    store.reset()
    resolveBootstrap({ data: { serverVersion: 'old', androidUpdate: { enabled: true, latestVersionCode: 2 } } })
    await pending

    expect(store.checked).toBe(false)
    expect(store.bootstrap).toBeNull()
    expect(store.clientInfo).toBeNull()
  })
})

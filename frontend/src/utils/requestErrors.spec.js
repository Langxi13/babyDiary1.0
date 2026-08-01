import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  emitRequestDiagnostic,
  requestErrorCategory,
  requestErrorMessage,
  resetRequestErrorNotices,
  shouldNotifyRequestError
} from './requestErrors'

describe('request error classification', () => {
  beforeEach(() => resetRequestErrorNotices())

  it('distinguishes cancellation, timeout, offline and unreachable failures', () => {
    expect(requestErrorCategory({ code: 'ERR_CANCELED' })).toBe('cancelled')
    expect(requestErrorCategory({ code: 'ECONNABORTED' })).toBe('timeout')
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false })
    expect(requestErrorCategory({ code: 'ERR_NETWORK' })).toBe('offline')
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
    expect(requestErrorCategory({ code: 'ERR_NETWORK' })).toBe('unreachable')
    expect(requestErrorMessage('offline')).toContain('设备已离线')
  })

  it('deduplicates repeated notices within the display window', () => {
    expect(shouldNotifyRequestError('unreachable', 1000)).toBe(true)
    expect(shouldNotifyRequestError('unreachable', 2000)).toBe(false)
    expect(shouldNotifyRequestError('unreachable', 5000)).toBe(true)
    expect(shouldNotifyRequestError('cancelled', 6000)).toBe(false)
  })

  it('emits only a normalized route template without query data', () => {
    const listener = vi.fn()
    window.addEventListener('app:request-diagnostic', listener, { once: true })

    emitRequestDiagnostic({
      config: {
        method: 'get',
        url: 'https://private.example.com/api/v3/spaces/08dc5c56-cf74-4d03-a8aa-6d65be485260/media?token=secret',
        __startedAt: Date.now() - 20
      }
    }, 'unreachable')

    expect(listener).toHaveBeenCalledOnce()
    expect(listener.mock.calls[0][0].detail).toMatchObject({
      category: 'unreachable',
      method: 'GET',
      path: '/api/v3/spaces/:id/media'
    })
    expect(JSON.stringify(listener.mock.calls[0][0].detail)).not.toContain('secret')
    expect(JSON.stringify(listener.mock.calls[0][0].detail)).not.toContain('private.example.com')
  })
})

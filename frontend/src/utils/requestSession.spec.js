import axios from 'axios'
import { describe, expect, it, vi } from 'vitest'
import request from './request'
import { advanceClientSessionGeneration } from './sessionScope'
import { resetClientSession } from './sessionBoundary'

describe('HTTP session boundary', () => {
  it('rejects a response that belongs to the previous account session', async () => {
    let finishRequest
    const pending = request.get('/api/session-boundary-test', {
      adapter: config => new Promise(resolve => {
        finishRequest = () => resolve({
          config,
          data: { code: 200, data: { owner: 'account-a' } },
          headers: {},
          status: 200,
          statusText: 'OK'
        })
      })
    })

    await vi.waitFor(() => expect(finishRequest).toBeTypeOf('function'))
    advanceClientSessionGeneration()
    finishRequest()

    await expect(pending).rejects.toSatisfy(error => axios.isCancel(error))
  })

  it('aborts an in-flight refresh request when the account session resets', async () => {
    localStorage.setItem('token', 'account-a-token')
    let refreshSignal
    const refreshSpy = vi.spyOn(axios, 'post').mockImplementation((_url, _data, config) => new Promise((resolve, reject) => {
      refreshSignal = config.signal
      refreshSignal.addEventListener('abort', () => reject(new axios.CanceledError('aborted')))
    }))
    try {
      const pending = request.get('/api/private-session-test', {
        adapter: config => Promise.reject(new axios.AxiosError(
          'Unauthorized',
          axios.AxiosError.ERR_BAD_REQUEST,
          config,
          null,
          { config, data: {}, headers: {}, status: 401, statusText: 'Unauthorized' }
        ))
      })

      await vi.waitFor(() => expect(refreshSignal).toBeInstanceOf(AbortSignal))
      resetClientSession('refresh-abort-test')

      expect(refreshSignal.aborted).toBe(true)
      await expect(pending).rejects.toSatisfy(error => axios.isCancel(error))
    } finally {
      refreshSpy.mockRestore()
    }
  })
})

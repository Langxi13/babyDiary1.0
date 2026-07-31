import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  clearApiCache: vi.fn(),
  openStepUpDialog: vi.fn()
}))

vi.mock('@/utils/apiCache', () => ({ clearApiCache: mocks.clearApiCache }))
vi.mock('@/utils/stepUpDialog', () => ({ openStepUpDialog: mocks.openStepUpDialog }))

import { getStepUpToken, requestStepUp } from './stepUp'

describe('step-up cache boundary', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mocks.clearApiCache.mockReset()
    mocks.openStepUpDialog.mockReset()
  })

  it('clears cached protected projections when a stored token expires', () => {
    sessionStorage.setItem('stepUpToken', 'expired-token')
    sessionStorage.setItem('stepUpExpiresAt', '1')

    expect(getStepUpToken()).toBe('')
    expect(sessionStorage.getItem('stepUpToken')).toBeNull()
    expect(mocks.clearApiCache).toHaveBeenCalledOnce()
  })

  it('clears standard projections after a new elevated session is created', async () => {
    mocks.openStepUpDialog.mockResolvedValue({
      token: 'fresh-token',
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    })

    await expect(requestStepUp()).resolves.toBe('fresh-token')
    expect(getStepUpToken()).toBe('fresh-token')
    expect(mocks.clearApiCache).toHaveBeenCalledOnce()
  })
})

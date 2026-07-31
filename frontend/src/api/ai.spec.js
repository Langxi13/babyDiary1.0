import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({ default: { get: mocks.get } }))

import { aiApi } from './ai'

describe('AI report history API', () => {
  it('delegates filtering and pagination to the server', async () => {
    const page = { content: [], pageNumber: 2, pageSize: 10, totalElements: 25, totalPages: 3 }
    mocks.get.mockResolvedValueOnce(page)

    await expect(aiApi.listReports('space-1', { type: 'MONTHLY', page: 2, size: 10 }))
      .resolves.toBe(page)
    expect(mocks.get).toHaveBeenCalledWith('/api/v3/spaces/space-1/ai-reports', {
      params: { type: 'MONTHLY', page: 2, size: 10 }
    })
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), invalidate: vi.fn() }))

vi.mock('@/utils/request', () => ({ default: { get: mocks.get, post: mocks.post } }))
vi.mock('@/utils/apiCache', () => ({
  cachedRequest: (_key, loader) => loader(),
  invalidateApiCache: mocks.invalidate,
  stableStringify: value => JSON.stringify(value)
}))
vi.mock('@/utils/stepUp', () => ({
  getStepUpToken: () => null,
  withStepUpRetry: callback => callback(null)
}))

import { diaryApi } from './diary'

describe('diary timeline paging', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.invalidate.mockReset()
  })

  it('loads every cursor page and translates month filters to a date range', async () => {
    mocks.get
      .mockResolvedValueOnce({
        items: [{ id: 'd-1', diaryDate: '2026-07-31', media: [] }],
        nextCursor: 'cursor-1',
        totalElements: null
      })
      .mockResolvedValueOnce({
        items: [{ id: 'd-2', diaryDate: '2026-07-01', media: [] }],
        nextCursor: null,
        totalElements: null
      })

    const groups = await diaryApi.getTimeline('space-1', {
      year: 2026,
      month: 7,
      mood: 'HAPPY'
    })

    expect(groups).toEqual([
      expect.objectContaining({ month: '2026-07', diaries: expect.arrayContaining([
        expect.objectContaining({ id: 'd-1' }),
        expect.objectContaining({ id: 'd-2' })
      ]) })
    ])
    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(mocks.get.mock.calls[0][1].params).toEqual(expect.objectContaining({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      mood: 'HAPPY',
      size: 50,
      includeTotal: false,
      cursor: undefined
    }))
    expect(mocks.get.mock.calls[1][1].params.cursor).toBe('cursor-1')
  })

  it('rejects a repeated cursor instead of looping forever', async () => {
    mocks.get.mockResolvedValue({ items: [], nextCursor: 'same-cursor' })

    await expect(diaryApi.getTimeline('space-1', {}))
      .rejects.toThrow('时间轴分页游标重复')
    expect(mocks.get).toHaveBeenCalledTimes(2)
  })

  it('updates local version state and invalidates reads after restoring a revision', async () => {
    mocks.post.mockResolvedValue({ id: 'diary-1', version: 7, media: [] })

    await expect(diaryApi.restoreRevision('space-1', 'diary-1', 'revision-1', 6, 'step-token'))
      .resolves.toEqual(expect.objectContaining({ id: 'diary-1', version: 7 }))
    expect(mocks.post).toHaveBeenCalledWith(
      '/api/v3/spaces/space-1/diaries/diary-1/revisions/revision-1/restore',
      null,
      { headers: { 'X-Step-Up-Token': 'step-token', 'If-Match': '"6"' } }
    )
    expect(mocks.invalidate).toHaveBeenCalledWith('spaces:space-1:diaries:')
  })
})

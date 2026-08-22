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

describe('bounded diary reads', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.invalidate.mockReset()
  })

  it('loads the timeline index and only the selected month first page', async () => {
    mocks.get
      .mockResolvedValueOnce({
        years: [{ year: 2026, count: 2, mediaCount: 3, months: [
          { month: '2026-07', count: 2, mediaCount: 3 }
        ] }]
      })
      .mockResolvedValueOnce({
        items: [{ id: 'd-1', diaryDate: '2026-07-31', previews: [] }],
        nextCursor: 'cursor-1',
        totalElements: null
      })

    const groups = await diaryApi.getTimeline('space-1', {
      year: 2026,
      month: 7,
      mood: 'HAPPY'
    })

    expect(groups).toEqual([
      expect.objectContaining({
        month: '2026-07',
        diaryCount: 2,
        mediaCount: 3,
        diaries: [expect.objectContaining({ id: 'd-1' })],
        nextCursor: 'cursor-1',
        loaded: true
      })
    ])
    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(mocks.get.mock.calls[0][0]).toBe('/api/v3/spaces/space-1/diaries/timeline')
    expect(mocks.get.mock.calls[1][0]).toBe('/api/v3/spaces/space-1/diaries/summaries')
    expect(mocks.get.mock.calls[1][1].params).toEqual(expect.objectContaining({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      mood: 'HAPPY',
      size: 20,
      includeTotal: false,
      cursor: undefined
    }))
  })

  it('fetches exactly one summary page for an explicit month cursor', async () => {
    mocks.get.mockResolvedValue({ items: [], nextCursor: 'next-cursor' })

    await expect(diaryApi.getTimelineMonth('space-1', {
      month: '2026-06', cursor: 'current-cursor'
    })).resolves.toEqual({ diaries: [], nextCursor: 'next-cursor' })
    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(mocks.get.mock.calls[0][1].params.cursor).toBe('current-cursor')
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

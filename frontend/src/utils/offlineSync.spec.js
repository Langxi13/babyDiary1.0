import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  pull: vi.fn(),
  push: vi.fn(),
  getDiary: vi.fn(),
  updateDiary: vi.fn(),
  invalidateDiaryReads: vi.fn(),
  uploadSource: vi.fn(),
  removeMedia: vi.fn(),
  releaseNativeImage: vi.fn(),
  listOperations: vi.fn(),
  removeOperations: vi.fn(),
  getMeta: vi.fn(),
  setMeta: vi.fn(),
  clearCache: vi.fn()
}))

vi.mock('@/api/workspace', () => ({
  workspaceApi: { sync: { pull: mocks.pull, push: mocks.push } }
}))
vi.mock('@/api/diary', () => ({
  diaryApi: { getDiary: mocks.getDiary, update: mocks.updateDiary },
  invalidateDiaryReads: mocks.invalidateDiaryReads
}))
vi.mock('@/api/media', () => ({
  mediaApi: { uploadSource: mocks.uploadSource, remove: mocks.removeMedia }
}))
vi.mock('@/platform/nativeImages', () => ({
  releaseNativeImage: mocks.releaseNativeImage
}))
vi.mock('@/utils/offlineDb', () => ({
  getOfflineMeta: mocks.getMeta,
  listOfflineOperations: mocks.listOperations,
  removeOfflineOperations: mocks.removeOperations,
  setOfflineMeta: mocks.setMeta,
  clearOfflineSessionCache: mocks.clearCache
}))
vi.mock('@/utils/stepUp', () => ({ getStepUpToken: () => null }))

import { syncWorkspace } from './offlineSync'

describe('offline media synchronization', () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset())
    mocks.pull.mockResolvedValue({
      changes: [],
      nextCursor: 1,
      hasMore: false,
      resetRequired: false,
      baselineCursor: 0
    })
    mocks.getMeta.mockResolvedValue(0)
    mocks.getDiary.mockResolvedValue({ id: 'diary-1', media: [], version: 1 })
    mocks.updateDiary.mockResolvedValue({ id: 'diary-1', version: 2 })
    mocks.uploadSource.mockResolvedValue({ id: 'media-1' })
  })

  it('uploads queued media and links the returned public media id to its diary', async () => {
    const pending = {
      id: 'operation-1',
      kind: 'media',
      diaryId: 'diary-1',
      file: new File(['image'], 'memory.png', { type: 'image/png' }),
      filename: 'memory.png',
      caption: 'Memory'
    }
    mocks.listOperations.mockResolvedValue([pending])

    await expect(syncWorkspace('space-1')).resolves.toEqual({
      synced: 1,
      conflicts: [],
      failures: []
    })

    expect(mocks.uploadSource).toHaveBeenCalledWith('space-1', {
      file: pending.file,
      uploadId: 'operation-1'
    }, {
      caption: 'Memory'
    })
    expect(mocks.updateDiary).toHaveBeenCalledWith(
      'space-1',
      'diary-1',
      expect.objectContaining({ mediaIds: ['media-1'] }),
      1,
      null
    )
    expect(mocks.removeOperations).toHaveBeenCalledWith(['operation-1'])
    expect(mocks.releaseNativeImage).toHaveBeenCalledWith(pending.file)
  })

  it('retains native staging files when linking fails so the queue can retry', async () => {
    const source = {
      kind: 'native-uri',
      uploadId: crypto.randomUUID(),
      uri: 'file:///pending/memory.heic',
      stagedPath: 'pending-media/memory.heic'
    }
    mocks.listOperations.mockResolvedValue([{
      id: 'operation-2',
      kind: 'media',
      diaryId: 'diary-1',
      source
    }])
    mocks.updateDiary.mockRejectedValueOnce(new Error('network'))

    await syncWorkspace('space-1')

    expect(mocks.removeMedia).toHaveBeenCalledWith('space-1', 'media-1')
    expect(mocks.removeOperations).not.toHaveBeenCalledWith(['operation-2'])
    expect(mocks.releaseNativeImage).not.toHaveBeenCalled()
  })
})

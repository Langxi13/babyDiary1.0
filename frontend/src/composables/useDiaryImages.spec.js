import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const nativeMocks = vi.hoisted(() => ({ release: vi.fn() }))

vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}))
vi.mock('@/platform/nativeImages', () => ({ releaseNativeImage: nativeMocks.release }))

import { useDiaryImages } from './useDiaryImages'

describe('useDiaryImages', () => {
  beforeEach(() => {
    nativeMocks.release.mockReset()
    nativeMocks.release.mockResolvedValue(undefined)
  })

  it('clears all existing images while retaining replacement uploads on the first edit', () => {
    const images = useDiaryImages({
      route: { fullPath: '/diaries/12/edit' },
      isEdit: ref(true)
    })
    images.setExistingImages([
      { id: 'old-a', mediaType: 'IMAGE', representations: { original: { url: '/media/old-a' } } },
      { id: 'old-b', mediaType: 'IMAGE', representations: { original: { url: '/media/old-b' } } }
    ])

    const replacement = new File(['replacement'], 'replacement.jpg', { type: 'image/jpeg' })
    images.fileList.value = [{
      name: replacement.name,
      raw: replacement,
      uid: 'replacement-1'
    }]

    const submission = images.buildImageSubmission()

    expect(submission.retainedMediaIds).toEqual([])
    expect(submission.newImages).toHaveLength(1)
    expect(submission.newImages[0]).toMatchObject({ file: replacement })
    expect(submission.newImages[0].uploadId).toMatch(/^[0-9a-f-]{36}$/)
    expect(submission.mediaOrder).toEqual(['new:0'])
  })

  it('keeps native URIs out of FormData and releases removed staging files', async () => {
    const images = useDiaryImages({
      route: { fullPath: '/diaries/create' },
      isEdit: ref(false)
    })
    const source = {
      kind: 'native-uri',
      uploadId: crypto.randomUUID(),
      name: 'memory.heic',
      type: 'image/heic',
      size: 2048,
      previewUrl: 'data:image/jpeg;base64,cHJldmlldw==',
      stagedPath: 'pending-media/memory.heic'
    }

    images.appendNativeFiles([source])
    expect(images.buildImageSubmission().newImages).toEqual([source])

    images.removeImageAt(0)
    await Promise.resolve()
    expect(nativeMocks.release).toHaveBeenCalledWith(source)
  })
})

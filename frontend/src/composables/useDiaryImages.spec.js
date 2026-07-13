import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}))

import { useDiaryImages } from './useDiaryImages'

describe('useDiaryImages', () => {
  it('clears all existing images while retaining replacement uploads on the first edit', () => {
    const images = useDiaryImages({
      route: { fullPath: '/diaries/12/edit' },
      isEdit: ref(true)
    })
    images.setExistingImages(['old-a.jpg', 'old-b.jpg'])

    const replacement = new File(['replacement'], 'replacement.jpg', { type: 'image/jpeg' })
    images.fileList.value = [{
      name: replacement.name,
      raw: replacement,
      uid: 'replacement-1'
    }]

    const formData = new FormData()
    images.appendImagesToFormData(formData)

    expect(formData.get('clearImages')).toBe('true')
    expect(formData.getAll('retainedImagePaths')).toEqual([])
    expect(formData.getAll('imageFiles')).toHaveLength(1)
    expect(formData.getAll('imageOrder')).toEqual(['new:0'])
  })
})

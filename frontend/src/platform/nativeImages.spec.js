import { beforeEach, describe, expect, it, vi } from 'vitest'

const camera = vi.hoisted(() => ({
  chooseFromGallery: vi.fn(),
  takePhoto: vi.fn()
}))

vi.mock('@capacitor/core', () => ({
  Capacitor: { convertFileSrc: value => value }
}))

vi.mock('@capacitor/camera', () => ({
  Camera: camera,
  EncodingType: { JPEG: 0 },
  MediaType: { Photo: 0, Video: 1 },
  MediaTypeSelection: { Photo: 0 }
}))

import { chooseNativeImages } from './nativeImages.js'

const photo = (format, extra = {}) => ({
  type: 0,
  webPath: `https://localhost/photo.${format}`,
  metadata: { format },
  ...extra
})

describe('native image normalization', () => {
  beforeEach(() => {
    camera.chooseFromGallery.mockReset()
    globalThis.fetch = vi.fn()
  })

  it('preserves a supported gallery format and enforces the requested limit', async () => {
    camera.chooseFromGallery.mockResolvedValue({ results: [photo('png'), photo('png')] })
    globalThis.fetch.mockResolvedValue({
      ok: true,
      blob: async () => new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' })
    })

    const files = await chooseNativeImages(1)

    expect(files).toHaveLength(1)
    expect(files[0].type).toBe('image/png')
    expect(files[0].name).toMatch(/\.png$/)
    expect(camera.chooseFromGallery).toHaveBeenCalledWith(expect.objectContaining({
      allowMultipleSelection: false,
      limit: 1
    }))
  })

  it('uses the native JPEG thumbnail when the original format is HEIC', async () => {
    camera.chooseFromGallery.mockResolvedValue({
      results: [photo('heic', { thumbnail: window.btoa('jpeg-preview') })]
    })

    const files = await chooseNativeImages(5)

    expect(files).toHaveLength(1)
    expect(files[0].type).toBe('image/jpeg')
    expect(files[0].name).toMatch(/\.jpg$/)
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })
})

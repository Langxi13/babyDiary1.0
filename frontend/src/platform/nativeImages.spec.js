import { beforeEach, describe, expect, it, vi } from 'vitest'

const camera = vi.hoisted(() => ({
  chooseFromGallery: vi.fn(),
  takePhoto: vi.fn()
}))
const filesystem = vi.hoisted(() => ({
  mkdir: vi.fn(),
  stat: vi.fn(),
  copy: vi.fn(),
  getUri: vi.fn(),
  deleteFile: vi.fn(),
  readdir: vi.fn()
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
vi.mock('@capacitor/filesystem', () => ({
  Directory: { Data: 'DATA' },
  Filesystem: filesystem
}))

import { chooseNativeImages, releaseNativeImage } from './nativeImages.js'

const photo = (format, extra = {}) => ({
  type: 0,
  webPath: `https://localhost/photo.${format}`,
  metadata: { format },
  ...extra
})

describe('native image normalization', () => {
  beforeEach(() => {
    camera.chooseFromGallery.mockReset()
    Object.values(filesystem).forEach(mock => mock.mockReset())
    filesystem.mkdir.mockResolvedValue(undefined)
    filesystem.copy.mockResolvedValue(undefined)
    filesystem.getUri.mockResolvedValue({ uri: 'file:///private/pending.heic' })
    filesystem.deleteFile.mockResolvedValue(undefined)
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

  it('uses the JPEG thumbnail when a web HEIC original cannot be read', async () => {
    camera.chooseFromGallery.mockResolvedValue({
      results: [photo('heic', { thumbnail: window.btoa('jpeg-preview') })]
    })

    const files = await chooseNativeImages(5)

    expect(files).toHaveLength(1)
    expect(files[0].type).toBe('image/jpeg')
    expect(files[0].name).toMatch(/\.jpg$/)
    expect(globalThis.fetch).toHaveBeenCalledOnce()
  })

  it('stages a native HEIC URI without loading the original through JavaScript', async () => {
    filesystem.stat.mockResolvedValue({ size: 4096 })
    camera.chooseFromGallery.mockResolvedValue({
      results: [photo('heic', {
        uri: 'content://photos/42',
        thumbnail: window.btoa('jpeg-preview'),
        metadata: { format: 'heic', size: 4096 }
      })]
    })

    const [source] = await chooseNativeImages(1)

    expect(source).toMatchObject({
      kind: 'native-uri',
      type: 'image/heic',
      size: 4096,
      uri: 'file:///private/pending.heic'
    })
    expect(source.uploadId).toMatch(/^[0-9a-f-]{36}$/)
    expect(source.previewUrl).toMatch(/^data:image\/jpeg;base64,/)
    expect(filesystem.copy).toHaveBeenCalledWith(expect.objectContaining({
      from: 'content://photos/42',
      toDirectory: 'DATA'
    }))
    expect(globalThis.fetch).not.toHaveBeenCalled()

    await releaseNativeImage(source)
    expect(filesystem.deleteFile).toHaveBeenCalledWith({
      directory: 'DATA',
      path: source.stagedPath
    })
  })
})

import { describe, expect, it } from 'vitest'
import { mediaOriginalUrl, mediaPreviewUrl, mediaThumbnailUrl, normalizeMedia } from './media'

describe('media model normalization', () => {
  it('resolves canonical representation URLs without creating legacy aliases', () => {
    const media = normalizeMedia({
      id: 'asset-1',
      mediaType: 'IMAGE',
      representations: {
        original: { variantType: 'ORIGINAL', profile: 'source', url: '/media/original?profile=source' },
        thumbnail: { variantType: 'THUMBNAIL', profile: 'default', url: '/media/thumb?profile=default' }
      }
    })

    expect(media.id).toBe('asset-1')
    expect(media).not.toHaveProperty('assetId')
    expect(mediaOriginalUrl(media)).toContain('profile=source')
    expect(mediaThumbnailUrl(media)).toContain('profile=default')
    expect(mediaPreviewUrl(media)).toContain('profile=default')
  })

  it('falls back to the original when a thumbnail representation is absent', () => {
    const media = normalizeMedia({
      id: 'asset-2',
      representations: {
        original: { variantType: 'ORIGINAL', profile: 'source', url: '/media/source' }
      }
    })

    expect(mediaOriginalUrl(media)).toBe('/media/source')
    expect(mediaThumbnailUrl(media)).toBe('/media/source')
    expect(mediaPreviewUrl(media)).toBe('/media/source')
  })

  it('uses the smaller original when JPEG conversion would increase transfer size', () => {
    const media = normalizeMedia({
      id: 'asset-3',
      representations: {
        original: { url: '/media/source', sizeBytes: 64000 },
        thumbnail: { url: '/media/thumb', sizeBytes: 110000 }
      }
    })

    expect(mediaPreviewUrl(media)).toBe('/media/source')
  })
})

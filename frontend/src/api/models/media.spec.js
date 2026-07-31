import { describe, expect, it } from 'vitest'
import { mediaOriginalUrl, mediaPreviewUrl, mediaThumbnailUrl, normalizeMedia } from './media'

describe('media model normalization', () => {
  it('resolves canonical representation URLs without creating legacy aliases', () => {
    const media = normalizeMedia({
      id: 'asset-1',
      mediaType: 'IMAGE',
      representations: {
        original: { variantType: 'ORIGINAL', profile: 'source', url: '/media/original?profile=source' },
        thumbnail: { variantType: 'THUMBNAIL', profile: 'compact', url: '/media/thumb?profile=compact' },
        preview: { variantType: 'PREVIEW', profile: 'screen', url: '/media/preview?profile=screen' }
      }
    })

    expect(media.id).toBe('asset-1')
    expect(media).not.toHaveProperty('assetId')
    expect(mediaOriginalUrl(media)).toContain('profile=source')
    expect(mediaThumbnailUrl(media)).toContain('profile=compact')
    expect(mediaPreviewUrl(media)).toContain('profile=screen')
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

  it('uses preview before original when compact is absent or not smaller', () => {
    const media = normalizeMedia({
      id: 'asset-3',
      representations: {
        original: { url: '/media/source', sizeBytes: 64000 },
        thumbnail: { url: '/media/thumb', sizeBytes: 110000 },
        preview: { url: '/media/preview', sizeBytes: 48000 }
      }
    })

    expect(mediaThumbnailUrl(media)).toBe('/media/preview')
    expect(mediaPreviewUrl(media)).toBe('/media/preview')
  })
})

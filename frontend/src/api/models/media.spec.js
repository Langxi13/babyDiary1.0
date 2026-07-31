import { describe, expect, it } from 'vitest'
import { mediaOriginalUrl, mediaThumbnailUrl, normalizeMedia } from './media'

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
  })
})

import { describe, expect, it } from 'vitest'
import { normalizeMedia } from './v3Adapters'

describe('V3 media normalization', () => {
  it('keeps migrated source originals and default thumbnails readable', () => {
    const media = normalizeMedia({
      id: 'asset-1',
      mediaType: 'IMAGE',
      variants: [
        { type: 'ORIGINAL', profile: 'source', status: 'READY', contentUrl: '/media/original?profile=source' },
        { type: 'THUMBNAIL', profile: 'default', status: 'READY', contentUrl: '/media/thumb?profile=default' }
      ]
    })

    expect(media.assetId).toBe('asset-1')
    expect(media.contentUrl).toContain('profile=source')
    expect(media.thumbnailUrl).toContain('profile=default')
  })

  it('prefers the source original deterministically when profiles share a type', () => {
    const media = normalizeMedia({
      id: 'asset-2',
      variants: [
        { type: 'ORIGINAL', profile: 'source', status: 'READY', contentUrl: '/media/source' },
        { type: 'ORIGINAL', profile: 'archive', status: 'READY', contentUrl: '/media/archive' },
        { type: 'ORIGINAL', profile: 'default', status: 'READY', contentUrl: '/media/default' }
      ]
    })

    expect(media.contentUrl).toBe('/media/source')
    expect(media.thumbnailUrl).toBe('/media/source')
  })
})

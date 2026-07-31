import { resolveServerUrl } from '@/platform/runtimeConfig'

const resolveUrl = value => value ? resolveServerUrl(value) : ''
const normalizeRepresentation = representation => representation ? {
  ...representation,
  url: resolveUrl(representation.url)
} : null

export const normalizeMedia = (media = {}) => ({
  ...media,
  representations: media.representations ? {
    original: normalizeRepresentation(media.representations.original),
    thumbnail: normalizeRepresentation(media.representations.thumbnail),
    poster: normalizeRepresentation(media.representations.poster),
    waveform: normalizeRepresentation(media.representations.waveform),
    transcoded: normalizeRepresentation(media.representations.transcoded)
  } : undefined
})

export const mediaOriginalUrl = media => media?.representations?.original?.url || ''
export const mediaThumbnailUrl = media => media?.representations?.thumbnail?.url || mediaOriginalUrl(media)
export const mediaPreviewUrl = media => {
  const original = media?.representations?.original
  const thumbnail = media?.representations?.thumbnail
  if (!thumbnail?.url) return original?.url || ''
  if (Number.isFinite(original?.sizeBytes) && Number.isFinite(thumbnail.sizeBytes) &&
      original.sizeBytes > 0 && thumbnail.sizeBytes >= original.sizeBytes) {
    return original.url || thumbnail.url
  }
  return thumbnail.url
}
export const mediaPosterUrl = media => media?.representations?.poster?.url || ''
export const mediaPlaybackUrl = media => media?.representations?.transcoded?.url || mediaOriginalUrl(media)

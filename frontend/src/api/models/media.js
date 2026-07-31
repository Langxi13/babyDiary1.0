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
    preview: normalizeRepresentation(media.representations.preview),
    poster: normalizeRepresentation(media.representations.poster),
    waveform: normalizeRepresentation(media.representations.waveform),
    transcoded: normalizeRepresentation(media.representations.transcoded)
  } : undefined
})

export const mediaOriginalUrl = media => media?.representations?.original?.url || ''
const usableDerivativeUrl = (representation, original) => {
  if (!representation?.url) return ''
  if (Number.isFinite(original?.sizeBytes) && Number.isFinite(representation.sizeBytes) &&
      original.sizeBytes > 0 && representation.sizeBytes >= original.sizeBytes) {
    return ''
  }
  return representation.url
}

export const mediaPreviewUrl = media => {
  const original = media?.representations?.original
  return usableDerivativeUrl(media?.representations?.preview, original) || original?.url || ''
}
export const mediaThumbnailUrl = media => {
  const original = media?.representations?.original
  return usableDerivativeUrl(media?.representations?.thumbnail, original) ||
    usableDerivativeUrl(media?.representations?.preview, original) ||
    original?.url || ''
}
export const mediaPosterUrl = media => media?.representations?.poster?.url || ''
export const mediaPlaybackUrl = media => media?.representations?.transcoded?.url || mediaOriginalUrl(media)

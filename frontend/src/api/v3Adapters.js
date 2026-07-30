import { resolveServerUrl } from '@/platform/runtimeConfig'

const resolveUrl = value => value ? resolveServerUrl(value) : ''

const normalizeRepresentation = representation => representation ? {
  ...representation,
  url: resolveUrl(representation.url)
} : null

export const normalizeMedia = (media = {}) => ({
  ...media,
  contentUrl: resolveUrl(media.contentUrl),
  thumbnailUrl: resolveUrl(media.thumbnailUrl),
  representations: media.representations ? {
    original: normalizeRepresentation(media.representations.original),
    thumbnail: normalizeRepresentation(media.representations.thumbnail),
    poster: normalizeRepresentation(media.representations.poster),
    waveform: normalizeRepresentation(media.representations.waveform),
    transcoded: normalizeRepresentation(media.representations.transcoded)
  } : undefined
})

export const mediaOriginalUrl = media => (
  media?.representations?.original?.url || media?.contentUrl || ''
)

export const mediaThumbnailUrl = media => (
  media?.representations?.thumbnail?.url || media?.thumbnailUrl || mediaOriginalUrl(media)
)

export const mediaPosterUrl = media => media?.representations?.poster?.url || ''

export const mediaPlaybackUrl = media => (
  media?.representations?.transcoded?.url || mediaOriginalUrl(media)
)

export const normalizeSpace = (space = {}) => ({
  ...space,
  editable: space.role !== 'VIEWER'
})

export const normalizeDiary = (diary = {}) => ({
  ...diary,
  tags: diary.tags || [],
  media: (diary.media || []).map(normalizeMedia)
})

export const diaryPayload = (value = {}) => ({
  clientId: value.clientId || undefined,
  title: value.title,
  diaryDate: value.diaryDate,
  contentHtml: value.contentHtml || '',
  mood: value.mood || null,
  visibility: value.visibility || 'PRIVATE',
  locked: !!value.locked,
  tagIds: value.tagIds || [],
  mediaIds: value.mediaIds || []
})

export const normalizeAnniversary = (item = {}) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const next = new Date(`${item.date}T00:00:00`)
  next.setFullYear(today.getFullYear())
  if (next < today) next.setFullYear(today.getFullYear() + 1)
  const original = new Date(`${item.date}T00:00:00`)
  return {
    ...item,
    coverMedia: item.coverMedia ? normalizeMedia(item.coverMedia) : null,
    daysUntil: Math.round((next - today) / 86400000),
    daysPassed: Math.max(0, Math.floor((today - original) / 86400000))
  }
}

export const normalizeAlbum = (album = {}) => ({
  ...album,
  editable: album.type !== 'SYSTEM',
  coverMedia: album.coverMedia ? normalizeMedia(album.coverMedia) : null
})

export const normalizeAlbumGroup = (group = {}) => ({
  ...group,
  editable: group.type !== 'SYSTEM',
  albums: (group.albums || []).map(normalizeAlbum)
})

export const normalizeDraft = (draft = {}) => ({
  ...draft,
  payload: draft.payload || {}
})

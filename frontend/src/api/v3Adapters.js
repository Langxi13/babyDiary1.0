import request from '@/utils/request'
import { resolveServerUrl } from '@/platform/runtimeConfig'

let spacesRequest = null

export const normalizeSpace = (space = {}) => ({
  ...space,
  spaceId: space.id,
  editable: space.role !== 'VIEWER'
})

export async function activeSpaceId() {
  const stored = localStorage.getItem('activeSpaceId')
  if (stored) return stored
  if (!spacesRequest) {
    spacesRequest = request.get('/api/v3/spaces').finally(() => { spacesRequest = null })
  }
  const response = await spacesRequest
  const first = response.data?.[0]
  if (!first?.id) throw new Error('当前账户没有可用日记空间')
  localStorage.setItem('activeSpaceId', first.id)
  return first.id
}

const profileRank = (type, profile) => type === 'ORIGINAL'
  ? profile === 'source' ? 0 : profile === 'default' ? 1 : 2
  : profile === 'default' ? 0 : profile === 'source' ? 1 : 2
const variant = (media, type) => (media?.variants || [])
  .filter(item => item.type === type && item.status === 'READY')
  .sort((left, right) => profileRank(type, left.profile) - profileRank(type, right.profile)
    || String(left.profile || '').localeCompare(String(right.profile || '')))[0]
const mediaUrl = value => value ? resolveServerUrl(value) : ''

export const normalizeMedia = (media = {}) => {
  const representations = media.representations || {}
  const original = representations.original || variant(media, 'ORIGINAL')
  const thumbnail = representations.thumbnail || variant(media, 'THUMBNAIL') || original
  const poster = representations.poster || variant(media, 'POSTER')
  const waveform = representations.waveform || variant(media, 'WAVEFORM')
  const transcoded = representations.transcoded || variant(media, 'TRANSCODED')
  const representationUrl = value => value?.url || value?.contentUrl
  const contentUrl = mediaUrl(media.contentUrl || representationUrl(original))
  return {
    ...media,
    assetId: media.id,
    contentUrl,
    thumbnailUrl: mediaUrl(media.thumbnailUrl || representationUrl(thumbnail)) || contentUrl,
    posterUrl: mediaUrl(representationUrl(poster)),
    waveformUrl: mediaUrl(representationUrl(waveform)),
    transcodedUrl: mediaUrl(representationUrl(transcoded)),
    mediaUrlExpiresAt: original?.expiresAt || null,
    filename: media.originalFilename
  }
}

export const normalizeTag = (tag = {}) => ({ ...tag, tagId: tag.id })

export const normalizeDiary = (diary = {}) => ({
  ...diary,
  diaryId: diary.id,
  publicId: diary.id,
  date: diary.diaryDate,
  content: diary.contentHtml ?? diary.contentText ?? '',
  contentFormat: diary.contentHtml == null ? 'plain' : 'html',
  moodKey: diary.mood || '',
  tags: (diary.tags || []).map(normalizeTag),
  media: (diary.media || []).map(normalizeMedia)
})

export const diaryPayload = (value = {}) => ({
  clientId: value.clientId || undefined,
  title: value.title,
  diaryDate: value.diaryDate || value.date,
  contentHtml: value.contentHtml ?? value.content ?? '',
  mood: value.mood ?? value.moodKey ?? null,
  visibility: value.visibility || 'PRIVATE',
  locked: !!value.locked,
  tagIds: value.tagIds || [],
  mediaIds: value.mediaIds || []
})

export const normalizeDraft = (draft = {}) => ({
  ...draft.payload,
  ...draft,
  draftId: draft.id,
  ...(draft.payload || {})
})

export const normalizeAnniversary = (item = {}, coverMedia = null) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const next = new Date(`${item.date}T00:00:00`)
  next.setFullYear(today.getFullYear())
  if (next < today) next.setFullYear(today.getFullYear() + 1)
  const daysUntil = Math.round((next - today) / 86400000)
  const original = new Date(`${item.date}T00:00:00`)
  return {
    ...item,
    anniversaryId: item.id,
    coverMedia: (coverMedia || item.coverMedia) ? normalizeMedia(coverMedia || item.coverMedia) : null,
    daysUntil,
    daysPassed: Math.max(0, Math.floor((today - original) / 86400000))
  }
}

export const normalizeAlbum = (album = {}) => ({
  ...album,
  albumId: album.id,
  photoCount: album.mediaCount || 0,
  editable: album.type !== 'SYSTEM',
  coverMedia: album.coverMedia ? normalizeMedia(album.coverMedia)
    : album.coverContentUrl ? { assetId: album.coverAssetId, contentUrl: mediaUrl(album.coverContentUrl),
      thumbnailUrl: mediaUrl(album.coverContentUrl) } : null
})

export const normalizeAlbumGroup = (group = {}) => ({
  ...group,
  groupId: group.id,
  editable: group.type !== 'SYSTEM',
  albums: (group.albums || []).map(normalizeAlbum)
})

if (typeof window !== 'undefined') {
  window.addEventListener('auth:session-reset', () => { spacesRequest = null })
}

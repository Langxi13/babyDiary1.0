import { normalizeMedia } from './media'

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

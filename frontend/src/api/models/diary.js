import { normalizeMedia } from './media'

export const normalizeDiary = (diary = {}) => ({
  ...diary,
  contentText: diary.contentText ?? diary.contentSnippet ?? '',
  tags: diary.tags || [],
  media: (diary.media || diary.previews || []).map(normalizeMedia),
  mediaCount: Number(diary.mediaCount ?? (diary.media || diary.previews || []).length)
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

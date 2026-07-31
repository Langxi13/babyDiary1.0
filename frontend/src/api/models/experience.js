import { normalizeMedia } from './media'

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

export const normalizeDraft = (draft = {}) => ({ ...draft, payload: draft.payload || {} })

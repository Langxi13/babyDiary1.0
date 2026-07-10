export const DEFAULT_TAG_COLORS = [
  '#ff7a90',
  '#f59f35',
  '#f7c948',
  '#58b368',
  '#40b3a2',
  '#4f8ef7',
  '#8f7cf6',
  '#7a8aa0'
]

export const formatLocalDate = (date = new Date()) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export const buildCreateDraftKey = (timestamp = Date.now()) => `create-${timestamp}`

const firstQueryValue = (value) => Array.isArray(value) ? value[0] : value

export const selectedDraftKey = (route) => {
  const draftKey = firstQueryValue(route?.query?.draftKey)
  return typeof draftKey === 'string' && draftKey.trim() ? draftKey.trim() : ''
}

export const isDraftEntryRoute = (route) => !!selectedDraftKey(route)

export const draftKeyFromRoute = (route, fallbackDraftKey) => {
  return selectedDraftKey(route) || fallbackDraftKey
}

export const nextTagColor = (existingTagCount = 0) => {
  const index = Math.max(0, Number(existingTagCount) || 0) % DEFAULT_TAG_COLORS.length
  return DEFAULT_TAG_COLORS[index]
}

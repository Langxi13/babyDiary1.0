import request from '@/utils/request'
import { activeSpaceId, diaryPayload, normalizeDiary, normalizeMedia } from '@/api/v3Adapters'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'

const listCursors = new Map()
const diaryVersions = new Map()

const filterKey = params => stableStringify({
  startDate: params.startDate,
  endDate: params.endDate,
  keyword: params.keyword,
  tagId: params.tagId,
  mood: params.moodKey || params.mood,
  size: params.size || 5
})

const normalizeListParams = params => ({
  startDate: params.startDate || undefined,
  endDate: params.endDate || undefined,
  keyword: params.keyword || undefined,
  tagId: params.tagId || undefined,
  mood: params.moodKey || params.mood || undefined,
  size: params.size || 5
})

const rememberVersion = diary => {
  if (diary?.id && Number.isInteger(diary.version)) diaryVersions.set(diary.id, diary.version)
  return diary
}

async function cursorForPage(spaceId, params, targetPage) {
  const key = `${spaceId}:${filterKey(params)}`
  let state = listCursors.get(key)
  if (!state) {
    state = { cursors: [null] }
    listCursors.set(key, state)
  }
  for (let page = state.cursors.length - 1; page < targetPage; page += 1) {
    const response = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
      params: { ...normalizeListParams(params), cursor: state.cursors[page] || undefined },
      headers: stepHeader(getStepUpToken())
    })
    if (!response.data.nextCursor) break
    state.cursors[page + 1] = response.data.nextCursor
  }
  return state.cursors[targetPage]
}

const uploadFiles = async (spaceId, files) => {
  const uploaded = []
  try {
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      const response = await request.post(`/api/v3/spaces/${spaceId}/media`, body, { timeout: 10 * 60 * 1000 })
      uploaded.push(normalizeMedia(response.data))
    }
    return uploaded
  } catch (error) {
    await cleanupUploads(spaceId, uploaded)
    throw error
  }
}

const cleanupUploads = (spaceId, uploaded) => Promise.allSettled(
  uploaded.map(media => request.delete(`/api/v3/spaces/${spaceId}/media/${media.assetId}`, { __silentError: true }))
)

const formDataCommand = async (spaceId, formData, editing) => {
  const files = formData.getAll('imageFiles').filter(value => value instanceof File)
  const uploaded = await uploadFiles(spaceId, files)
  const retained = formData.getAll('retainedAssetIds').map(String)
  const order = formData.getAll('mediaOrder').map(String)
  let mediaIds
  if (!editing) {
    mediaIds = uploaded.map(item => item.assetId)
  } else if (order.length) {
    mediaIds = order.map(entry => {
      const [kind, rawIndex] = entry.split(':', 2)
      return kind === 'new' ? uploaded[Number(rawIndex)]?.assetId : rawIndex
    }).filter(Boolean)
  } else {
    mediaIds = [...retained, ...uploaded.map(item => item.assetId)]
  }
  return {
    uploaded,
    command: diaryPayload({
      title: formData.get('title'),
      date: formData.get('date'),
      content: formData.get('content'),
      moodKey: formData.get('moodKey'),
      tagIds: String(formData.get('tagIds') || '').split(',').filter(Boolean),
      mediaIds
    })
  }
}

async function requiredVersion(spaceId, diaryId) {
  if (diaryVersions.has(diaryId)) return diaryVersions.get(diaryId)
  const response = await withStepUpRetry(token => request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}`,
    { headers: stepHeader(token) }))
  rememberVersion(response.data)
  return response.data.version
}

export const diaryApi = {
  getDiaryList(params = {}, options = {}) {
    return cachedRequest(
      `diaries:list:${stableStringify(params)}`,
      async () => {
        const spaceId = await activeSpaceId()
        const page = Math.max(0, Number(params.page) || 0)
        const cursor = await cursorForPage(spaceId, params, page)
        const response = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
          params: { ...normalizeListParams(params), cursor: cursor || undefined },
          headers: stepHeader(getStepUpToken())
        })
        const content = (response.data.items || []).map(item => rememberVersion(normalizeDiary(item)))
        const totalElements = Number(response.data.totalElements) || 0
        const pageSize = Number(params.size) || 5
        return { ...response, data: {
          content, pageNumber: page, pageSize, totalElements,
          totalPages: Math.ceil(totalElements / pageSize), nextCursor: response.data.nextCursor
        } }
      },
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  getDiary(id, options = {}) {
    return cachedRequest(
      `diaries:detail:${id}`,
      async () => {
        const spaceId = await activeSpaceId()
        const response = await withStepUpRetry(token => request.get(`/api/v3/spaces/${spaceId}/diaries/${id}`,
          { headers: stepHeader(token) }))
        return { ...response, data: rememberVersion(normalizeDiary(response.data)) }
      },
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  async createDiary(formData) {
    const spaceId = await activeSpaceId()
    const prepared = await formDataCommand(spaceId, formData, false)
    try {
      const response = await request.post(`/api/v3/spaces/${spaceId}/diaries`, prepared.command)
      invalidateDiaryReads()
      return { ...response, data: rememberVersion(normalizeDiary(response.data)) }
    } catch (error) {
      await cleanupUploads(spaceId, prepared.uploaded)
      throw error
    }
  },

  async updateDiary(id, formData) {
    const spaceId = await activeSpaceId()
    const prepared = await formDataCommand(spaceId, formData, true)
    try {
      const version = await requiredVersion(spaceId, id)
      const response = await withStepUpRetry(token => request.put(`/api/v3/spaces/${spaceId}/diaries/${id}`,
        prepared.command, { headers: { ...stepHeader(token), 'If-Match': `"${version}"` } }))
      invalidateDiaryReads()
      invalidateApiCache(`diaries:detail:${id}`)
      return { ...response, data: rememberVersion(normalizeDiary(response.data)) }
    } catch (error) {
      await cleanupUploads(spaceId, prepared.uploaded)
      throw error
    }
  },

  async deleteDiary(id) {
    const spaceId = await activeSpaceId()
    const version = await requiredVersion(spaceId, id)
    const response = await withStepUpRetry(token => request.delete(`/api/v3/spaces/${spaceId}/diaries/${id}`, {
      headers: { ...stepHeader(token), 'If-Match': `"${version}"` }
    }))
    diaryVersions.delete(id)
    invalidateDiaryReads()
    invalidateApiCache(`diaries:detail:${id}`)
    return response
  },

  exportImages() {
    return Promise.reject(new Error('V3 图片导出正在迁移，请从相册下载原图'))
  },

  async getTimeline(params = {}, options = {}) {
    return cachedRequest(`diaries:timeline:${stableStringify(params)}`, async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
        params: { ...normalizeListParams(params), size: 50 }, headers: stepHeader(getStepUpToken())
      })
      const diaries = (response.data.items || []).map(item => rememberVersion(normalizeDiary(item)))
      const groups = new Map()
      for (const diary of diaries) {
        const month = diary.date?.slice(0, 7)
        if (!month) continue
        if (!groups.has(month)) groups.set(month, { month, diaries: [] })
        groups.get(month).diaries.push(diary)
      }
      return { ...response, data: [...groups.values()] }
    }, { ttl: options.ttl ?? 120000, force: options.force })
  },

  async getCalendar(params = {}, options = {}) {
    return cachedRequest(`diaries:calendar:${stableStringify(params)}`, async () => {
      const spaceId = await activeSpaceId()
      const month = `${params.year}-${String(params.month).padStart(2, '0')}`
      const response = await request.get(`/api/v3/spaces/${spaceId}/diaries/calendar`, {
        params: { month }, headers: stepHeader(getStepUpToken())
      })
      return { ...response, data: (response.data.days || []).map(day => ({
        ...day, firstTitle: day.entries?.[0]?.title || '', firstDiaryId: day.entries?.[0]?.diaryId || null
      })) }
    }, { ttl: options.ttl ?? 120000, force: options.force })
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

export function invalidateDiaryReads() {
  listCursors.clear()
  invalidateApiCache('diaries:')
  invalidateApiCache('photos:')
  invalidateApiCache('albums:')
}

if (typeof window !== 'undefined') {
  window.addEventListener('auth:session-reset', () => {
    listCursors.clear()
    diaryVersions.clear()
  })
}

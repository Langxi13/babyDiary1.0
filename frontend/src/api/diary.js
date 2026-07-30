import request from '@/utils/request'
import { diaryPayload, normalizeDiary, normalizeMedia } from '@/api/v3Adapters'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'

const listCursors = new Map()
const diaryVersions = new Map()

const filterKey = params => stableStringify({
  startDate: params.startDate,
  endDate: params.endDate,
  keyword: params.keyword,
  tagId: params.tagId,
  mood: params.mood,
  trash: !!params.trash,
  size: params.size || 5
})

const normalizeListParams = params => ({
  startDate: params.startDate || undefined,
  endDate: params.endDate || undefined,
  keyword: params.keyword || undefined,
  tagId: params.tagId || undefined,
  mood: params.mood || undefined,
  trash: params.trash || undefined,
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
    const result = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
      params: { ...normalizeListParams(params), cursor: state.cursors[page] || undefined },
      headers: stepHeader(getStepUpToken())
    })
    if (!result.nextCursor) break
    state.cursors[page + 1] = result.nextCursor
  }
  return state.cursors[targetPage]
}

const uploadFiles = async (spaceId, files) => {
  const uploaded = []
  try {
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      uploaded.push(normalizeMedia(await request.post(`/api/v3/spaces/${spaceId}/media`, body, {
        timeout: 10 * 60 * 1000
      })))
    }
    return uploaded
  } catch (error) {
    await cleanupUploads(spaceId, uploaded)
    throw error
  }
}

const cleanupUploads = (spaceId, uploaded) => Promise.allSettled(
  uploaded.map(media => request.delete(`/api/v3/spaces/${spaceId}/media/${media.id}`, { __silentError: true }))
)

const formDataCommand = async (spaceId, formData, editing) => {
  const files = formData.getAll('imageFiles').filter(value => value instanceof File)
  const uploaded = await uploadFiles(spaceId, files)
  const retained = formData.getAll('retainedMediaIds').map(String)
  const order = formData.getAll('mediaOrder').map(String)
  let mediaIds
  if (!editing) {
    mediaIds = uploaded.map(item => item.id)
  } else if (order.length) {
    mediaIds = order.map(entry => {
      const [kind, rawIndex] = entry.split(':', 2)
      return kind === 'new' ? uploaded[Number(rawIndex)]?.id : rawIndex
    }).filter(Boolean)
  } else {
    mediaIds = [...retained, ...uploaded.map(item => item.id)]
  }
  return {
    uploaded,
    command: diaryPayload({
      title: formData.get('title'),
      diaryDate: formData.get('diaryDate'),
      contentHtml: formData.get('contentHtml'),
      mood: formData.get('mood'),
      visibility: formData.get('visibility') || undefined,
      locked: formData.get('locked') === 'true',
      tagIds: String(formData.get('tagIds') || '').split(',').filter(Boolean),
      mediaIds
    })
  }
}

async function requiredVersion(spaceId, diaryId) {
  if (diaryVersions.has(diaryId)) return diaryVersions.get(diaryId)
  const diary = normalizeDiary(await withStepUpRetry(token => request.get(
    `/api/v3/spaces/${spaceId}/diaries/${diaryId}`,
    { headers: stepHeader(token) }
  )))
  rememberVersion(diary)
  return diary.version
}

export const diaryApi = {
  getDiaryList(spaceId, params = {}, options = {}) {
    return cachedRequest(
      `spaces:${spaceId}:diaries:list:${stableStringify(params)}`,
      async () => {
        const page = Math.max(0, Number(params.page) || 0)
        const cursor = await cursorForPage(spaceId, params, page)
        const result = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
          params: { ...normalizeListParams(params), cursor: cursor || undefined },
          headers: stepHeader(getStepUpToken())
        })
        const content = (result.items || []).map(item => rememberVersion(normalizeDiary(item)))
        const totalElements = Number(result.totalElements) || 0
        const pageSize = Number(params.size) || 5
        return {
          content,
          pageNumber: page,
          pageSize,
          totalElements,
          totalPages: Math.ceil(totalElements / pageSize),
          nextCursor: result.nextCursor
        }
      },
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  getDiary(spaceId, id, options = {}) {
    return cachedRequest(
      `spaces:${spaceId}:diaries:detail:${id}`,
      async () => rememberVersion(normalizeDiary(await withStepUpRetry(token => request.get(
        `/api/v3/spaces/${spaceId}/diaries/${id}`,
        { headers: stepHeader(token) }
      )))),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },

  async createDiary(spaceId, formData) {
    const prepared = await formDataCommand(spaceId, formData, false)
    try {
      return await this.create(spaceId, prepared.command)
    } catch (error) {
      await cleanupUploads(spaceId, prepared.uploaded)
      throw error
    }
  },

  async updateDiary(spaceId, id, formData) {
    const prepared = await formDataCommand(spaceId, formData, true)
    try {
      return await this.update(spaceId, id, prepared.command, await requiredVersion(spaceId, id))
    } catch (error) {
      await cleanupUploads(spaceId, prepared.uploaded)
      throw error
    }
  },

  async deleteDiary(spaceId, id) {
    await this.moveToTrash(spaceId, id, await requiredVersion(spaceId, id))
  },

  async create(spaceId, command) {
    const diary = rememberVersion(normalizeDiary(await request.post(
      `/api/v3/spaces/${spaceId}/diaries`, diaryPayload(command)
    )))
    invalidateDiaryReads(spaceId)
    return diary
  },

  async update(spaceId, id, command, version, stepUpToken) {
    const diary = rememberVersion(normalizeDiary(await withStepUpRetry(token => request.put(
      `/api/v3/spaces/${spaceId}/diaries/${id}`,
      diaryPayload(command),
      { headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` } }
    ))))
    invalidateDiaryReads(spaceId)
    invalidateApiCache(`spaces:${spaceId}:diaries:detail:${id}`)
    return diary
  },

  async moveToTrash(spaceId, id, version, stepUpToken) {
    await withStepUpRetry(token => request.delete(`/api/v3/spaces/${spaceId}/diaries/${id}`, {
      headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` }
    }))
    diaryVersions.delete(id)
    invalidateDiaryReads(spaceId)
    invalidateApiCache(`spaces:${spaceId}:diaries:detail:${id}`)
  },

  async restore(spaceId, id, version, stepUpToken) {
    const diary = rememberVersion(normalizeDiary(await withStepUpRetry(token => request.post(
      `/api/v3/spaces/${spaceId}/diaries/${id}/restore`,
      null,
      { headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` } }
    ))))
    invalidateDiaryReads(spaceId)
    return diary
  },

  revisions: (spaceId, id, stepUpToken) => request.get(
    `/api/v3/spaces/${spaceId}/diaries/${id}/revisions`,
    { headers: stepHeader(stepUpToken) }
  ),

  restoreRevision: async (spaceId, id, revisionId, version, stepUpToken) => normalizeDiary(
    await request.post(
      `/api/v3/spaces/${spaceId}/diaries/${id}/revisions/${revisionId}/restore`,
      null,
      { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }
    )
  ),

  comments: (spaceId, id, stepUpToken) => request.get(
    `/api/v3/spaces/${spaceId}/diaries/${id}/comments`,
    { headers: stepHeader(stepUpToken) }
  ),

  addComment: (spaceId, id, content, stepUpToken) => request.post(
    `/api/v3/spaces/${spaceId}/diaries/${id}/comments`,
    { content },
    { headers: stepHeader(stepUpToken) }
  ),

  updateComment: (spaceId, id, commentId, content, stepUpToken) => request.put(
    `/api/v3/spaces/${spaceId}/diaries/${id}/comments/${commentId}`,
    { content },
    { headers: stepHeader(stepUpToken) }
  ),

  removeComment: (spaceId, id, commentId, stepUpToken) => request.delete(
    `/api/v3/spaces/${spaceId}/diaries/${id}/comments/${commentId}`,
    { headers: stepHeader(stepUpToken) }
  ),

  reactions: (spaceId, id, stepUpToken) => request.get(
    `/api/v3/spaces/${spaceId}/diaries/${id}/reactions`,
    { headers: stepHeader(stepUpToken) }
  ),

  setReaction: (spaceId, id, emoji, active, stepUpToken) => request.put(
    `/api/v3/spaces/${spaceId}/diaries/${id}/reactions`,
    { emoji, active },
    { headers: stepHeader(stepUpToken) }
  ),

  exportImages() {
    return Promise.reject(new Error('图片导出请从相册下载原图'))
  },

  getTimeline(spaceId, params = {}, options = {}) {
    return cachedRequest(`spaces:${spaceId}:diaries:timeline:${stableStringify(params)}`, async () => {
      const result = await request.get(`/api/v3/spaces/${spaceId}/diaries`, {
        params: { ...normalizeListParams(params), size: 50 },
        headers: stepHeader(getStepUpToken())
      })
      const diaries = (result.items || []).map(item => rememberVersion(normalizeDiary(item)))
      const groups = new Map()
      for (const diary of diaries) {
        const month = diary.diaryDate?.slice(0, 7)
        if (!month) continue
        if (!groups.has(month)) groups.set(month, { month, diaries: [] })
        groups.get(month).diaries.push(diary)
      }
      return [...groups.values()]
    }, { ttl: options.ttl ?? 120000, force: options.force })
  },

  getCalendar(spaceId, params = {}, options = {}) {
    return cachedRequest(`spaces:${spaceId}:diaries:calendar:${stableStringify(params)}`, async () => {
      const month = `${params.year}-${String(params.month).padStart(2, '0')}`
      const result = await request.get(`/api/v3/spaces/${spaceId}/diaries/calendar`, {
        params: { month }, headers: stepHeader(getStepUpToken())
      })
      return (result.days || []).map(day => ({
        ...day,
        firstTitle: day.entries?.[0]?.title || '',
        firstDiaryId: day.entries?.[0]?.diaryId || null
      }))
    }, { ttl: options.ttl ?? 120000, force: options.force })
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

export function invalidateDiaryReads(spaceId) {
  if (spaceId) {
    invalidateApiCache(`spaces:${spaceId}:diaries:`)
    invalidateApiCache(`spaces:${spaceId}:photos:`)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
  } else {
    invalidateApiCache()
  }
  listCursors.clear()
}

if (typeof window !== 'undefined') {
  window.addEventListener('auth:session-reset', () => {
    listCursors.clear()
    diaryVersions.clear()
  })
}

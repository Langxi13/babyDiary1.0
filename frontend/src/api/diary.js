import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { diaryPayload, normalizeDiary, normalizeMedia } from '@/api/models'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'
import { mediaApi } from '@/api/media'
import { isNativeApp } from '@/platform/runtimeConfig'
import { downloadNativeFile } from '@/platform/nativeFiles'

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

const normalizeComment = comment => comment ? {
  ...comment,
  avatarMedia: comment.avatarMedia ? normalizeMedia(comment.avatarMedia) : null
} : comment

async function cursorForPage(spaceId, params, targetPage, stepUpToken) {
  const key = `${spaceId}:${filterKey(params)}:${accessMode(stepUpToken)}`
  let state = listCursors.get(key)
  if (!state) {
    state = { cursors: [null] }
    listCursors.set(key, state)
  }
  for (let page = state.cursors.length - 1; page < targetPage; page += 1) {
    const result = await request.get(`${API_ROOT}/spaces/${spaceId}/diaries/summaries`, {
      params: { ...normalizeListParams(params), cursor: state.cursors[page] || undefined },
      headers: stepHeader(stepUpToken)
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
      uploaded.push(normalizeMedia(await mediaApi.uploadSource(spaceId, file)))
    }
    return uploaded
  } catch (error) {
    await cleanupUploads(spaceId, uploaded)
    throw error
  }
}

const cleanupUploads = (spaceId, uploaded) => Promise.allSettled(
  uploaded.map(media => request.delete(`${API_ROOT}/spaces/${spaceId}/media/${media.id}`, { __silentError: true }))
)

const formDataCommand = async (spaceId, formData, editing, imageSubmission = null) => {
  const files = imageSubmission?.newImages ||
    formData.getAll('imageFiles').filter(value => value instanceof File)
  const uploaded = await uploadFiles(spaceId, files)
  const retained = imageSubmission?.retainedMediaIds || formData.getAll('retainedMediaIds').map(String)
  const order = imageSubmission?.mediaOrder || formData.getAll('mediaOrder').map(String)
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
    `${API_ROOT}/spaces/${spaceId}/diaries/${diaryId}`,
    { headers: stepHeader(token) }
  )))
  rememberVersion(diary)
  return diary.version
}

export const diaryApi = {
  getDiaryList(spaceId, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(
      `spaces:${spaceId}:diaries:list:${stableStringify(params)}:access:${accessMode(stepUpToken)}`,
      async () => {
        const page = Math.max(0, Number(params.page) || 0)
        const cursor = await cursorForPage(spaceId, params, page, stepUpToken)
        const result = await request.get(`${API_ROOT}/spaces/${spaceId}/diaries/summaries`, {
          params: { ...normalizeListParams(params), cursor: cursor || undefined },
          headers: stepHeader(stepUpToken)
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
      { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => !stepUpToken }
    )
  },

  getDiary(spaceId, id, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(
      `spaces:${spaceId}:diaries:detail:${id}:access:${accessMode(stepUpToken)}`,
      async () => rememberVersion(normalizeDiary(await withStepUpRetry(token => request.get(
        `${API_ROOT}/spaces/${spaceId}/diaries/${id}`,
        { headers: stepHeader(token || stepUpToken) }
      )))),
      { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => false }
    )
  },

  async createDiary(spaceId, formData, imageSubmission) {
    const prepared = await formDataCommand(spaceId, formData, false, imageSubmission)
    try {
      return await this.create(spaceId, prepared.command)
    } catch (error) {
      await cleanupUploads(spaceId, prepared.uploaded)
      throw error
    }
  },

  async updateDiary(spaceId, id, formData, imageSubmission) {
    const prepared = await formDataCommand(spaceId, formData, true, imageSubmission)
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
      `${API_ROOT}/spaces/${spaceId}/diaries`, diaryPayload(command)
    )))
    invalidateDiaryReads(spaceId)
    return diary
  },

  async update(spaceId, id, command, version, stepUpToken) {
    const diary = rememberVersion(normalizeDiary(await withStepUpRetry(token => request.put(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}`,
      diaryPayload(command),
      { headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` } }
    ))))
    invalidateDiaryReads(spaceId)
    invalidateApiCache(`spaces:${spaceId}:diaries:detail:${id}`)
    return diary
  },

  async moveToTrash(spaceId, id, version, stepUpToken) {
    await withStepUpRetry(token => request.delete(`${API_ROOT}/spaces/${spaceId}/diaries/${id}`, {
      headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` }
    }))
    diaryVersions.delete(id)
    invalidateDiaryReads(spaceId)
    invalidateApiCache(`spaces:${spaceId}:diaries:detail:${id}`)
  },

  async permanentlyDelete(spaceId, id, version, stepUpToken) {
    await withStepUpRetry(token => request.delete(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}/permanent`,
      { headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` } }
    ))
    diaryVersions.delete(id)
    invalidateDiaryReads(spaceId)
    invalidateApiCache(`spaces:${spaceId}:diaries:detail:${id}`)
  },

  async restore(spaceId, id, version, stepUpToken) {
    const diary = rememberVersion(normalizeDiary(await withStepUpRetry(token => request.post(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}/restore`,
      null,
      { headers: { ...stepHeader(token || stepUpToken), 'If-Match': `"${version}"` } }
    ))))
    invalidateDiaryReads(spaceId)
    return diary
  },

  revisions: (spaceId, id, stepUpToken) => request.get(
    `${API_ROOT}/spaces/${spaceId}/diaries/${id}/revisions`,
    { headers: stepHeader(stepUpToken) }
  ),

  async restoreRevision(spaceId, id, revisionId, version, stepUpToken) {
    const diary = rememberVersion(normalizeDiary(await request.post(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}/revisions/${revisionId}/restore`,
      null,
      { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }
    )))
    invalidateDiaryReads(spaceId)
    return diary
  },

  async comments(spaceId, id, stepUpToken) {
    return (await request.get(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}/comments`,
      { headers: stepHeader(stepUpToken) }
    ) || []).map(normalizeComment)
  },

  async addComment(spaceId, id, content, stepUpToken) {
    return normalizeComment(await request.post(
      `${API_ROOT}/spaces/${spaceId}/diaries/${id}/comments`,
      { content },
      { headers: stepHeader(stepUpToken) }
    ))
  },

  updateComment: (spaceId, id, commentId, content, stepUpToken) => request.put(
    `${API_ROOT}/spaces/${spaceId}/diaries/${id}/comments/${commentId}`,
    { content },
    { headers: stepHeader(stepUpToken) }
  ),

  removeComment: (spaceId, id, commentId, stepUpToken) => request.delete(
    `${API_ROOT}/spaces/${spaceId}/diaries/${id}/comments/${commentId}`,
    { headers: stepHeader(stepUpToken) }
  ),

  reactions: (spaceId, id, stepUpToken) => request.get(
    `${API_ROOT}/spaces/${spaceId}/diaries/${id}/reactions`,
    { headers: stepHeader(stepUpToken) }
  ),

  setReaction: (spaceId, id, emoji, active, stepUpToken) => request.put(
    `${API_ROOT}/spaces/${spaceId}/diaries/${id}/reactions`,
    { emoji, active },
    { headers: stepHeader(stepUpToken) }
  ),

  exportImages(spaceId, startDate, endDate) {
    return withStepUpRetry(token => isNativeApp()
      ? downloadNativeFile({
          path: `${API_ROOT}/spaces/${spaceId}/transfer/media`,
          params: { startDate, endDate },
          headers: stepHeader(token),
          filename: `Baby-Diary-images-${startDate}-${endDate}.zip`
        })
      : request.get(
          `${API_ROOT}/spaces/${spaceId}/transfer/media`,
          {
            params: { startDate, endDate },
            responseType: 'blob',
            headers: stepHeader(token),
            timeout: 5 * 60 * 1000
          }
        ))
  },

  getTimelineIndex(spaceId, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    const query = { mood: params.mood || undefined, tagId: params.tagId || undefined }
    return cachedRequest(`spaces:${spaceId}:diaries:timeline:index:${stableStringify(query)}:access:${accessMode(stepUpToken)}`, async () => {
      const result = await request.get(`${API_ROOT}/spaces/${spaceId}/diaries/timeline`, {
        params: query,
        headers: stepHeader(stepUpToken)
      })
      return (result.years || []).flatMap(year => (year.months || []).map(month => ({
        month: month.month,
        diaryCount: Number(month.count) || 0,
        mediaCount: Number(month.mediaCount) || 0,
        diaries: [],
        nextCursor: null,
        loaded: false
      })))
    }, { ttl: options.ttl ?? 120000, force: options.force, cacheIf: () => !stepUpToken })
  },

  getTimelineMonth(spaceId, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    const [year, month] = String(params.month || '').split('-').map(Number)
    if (!Number.isInteger(year) || !Number.isInteger(month)) {
      return Promise.reject(new Error('时间轴月份无效'))
    }
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`
    const endDate = new Date(Date.UTC(year, month, 0)).toISOString().slice(0, 10)
    const query = {
      startDate,
      endDate,
      mood: params.mood || undefined,
      tagId: params.tagId || undefined,
      cursor: params.cursor || undefined,
      size: Math.min(50, Math.max(1, Number(params.size) || 20)),
      includeTotal: false
    }
    return cachedRequest(`spaces:${spaceId}:diaries:timeline:month:${stableStringify(query)}:access:${accessMode(stepUpToken)}`, async () => {
      const result = await request.get(`${API_ROOT}/spaces/${spaceId}/diaries/summaries`, {
        params: query,
        headers: stepHeader(stepUpToken)
      })
      return {
        diaries: (result.items || []).map(item => rememberVersion(normalizeDiary(item))),
        nextCursor: result.nextCursor || null
      }
    }, { ttl: options.ttl ?? 120000, force: options.force, cacheIf: () => !stepUpToken })
  },

  async getTimeline(spaceId, params = {}, options = {}) {
    let groups = await this.getTimelineIndex(spaceId, params, options)
    if (Number.isInteger(params.year)) {
      groups = groups.filter(group => group.month.startsWith(`${params.year}-`) &&
        (!Number.isInteger(params.month) || group.month === `${params.year}-${String(params.month).padStart(2, '0')}`))
    }
    if (!groups.length) return groups
    const selectedMonth = Number.isInteger(params.year) && Number.isInteger(params.month)
      ? `${params.year}-${String(params.month).padStart(2, '0')}`
      : groups[0].month
    const page = await this.getTimelineMonth(spaceId, { ...params, month: selectedMonth }, options)
    return groups.map(group => group.month === selectedMonth
      ? { ...group, ...page, loaded: true }
      : group)
  },

  getCalendar(spaceId, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(`spaces:${spaceId}:diaries:calendar:${stableStringify(params)}:access:${accessMode(stepUpToken)}`, async () => {
      const month = `${params.year}-${String(params.month).padStart(2, '0')}`
      const result = await request.get(`${API_ROOT}/spaces/${spaceId}/diaries/calendar`, {
        params: { month }, headers: stepHeader(stepUpToken)
      })
      return (result.days || []).map(day => ({
        ...day,
        firstTitle: day.entries?.[0]?.title || '',
        firstDiaryId: day.entries?.[0]?.diaryId || null
      }))
    }, { ttl: options.ttl ?? 120000, force: options.force, cacheIf: () => !stepUpToken })
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

function accessMode(token) {
  return token ? 'elevated' : 'standard'
}

export function invalidateDiaryReads(spaceId) {
  if (spaceId) {
    invalidateApiCache(`spaces:${spaceId}:diaries:`)
    invalidateApiCache(`spaces:${spaceId}:photos:`)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    invalidateApiCache(`spaces:${spaceId}:home:`)
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

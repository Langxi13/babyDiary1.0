import request from '@/utils/request'
import {
  activeSpaceId, normalizeAnniversary, normalizeDraft, normalizeMedia, normalizeTag
} from '@/api/v3Adapters'
import { invalidateDiaryReads } from '@/api/diary'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const tagApi = {
  list(options = {}) {
    return cachedRequest('tags:list', async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/tags`)
      return { ...response, data: (response.data || []).map(normalizeTag) }
    }, { ttl: options.ttl ?? 600000, force: options.force })
  },
  async create(payload) {
    const spaceId = await activeSpaceId()
    const response = await request.post(`/api/v3/spaces/${spaceId}/tags`, payload)
    invalidateApiCache('tags:')
    invalidateDiaryReads()
    return { ...response, data: normalizeTag(response.data) }
  }
}

const anniversaryPayload = value => ({
  title: value.title,
  date: value.date,
  description: value.description || null,
  coverAssetId: value.coverAssetId || null,
  sortOrder: Number(value.sortOrder ?? value.sort) || 0
})

export const anniversaryApi = {
  list(options = {}) {
    return cachedRequest('anniversaries:list', async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/anniversaries`, { headers: stepHeader(getStepUpToken()) })
      return { ...response, data: (response.data || []).map(item => normalizeAnniversary(item)) }
    }, { ttl: options.ttl ?? 600000, force: options.force })
  },
  async create(payload) {
    const spaceId = await activeSpaceId()
    const response = await withStepUpRetry(token => request.post(`/api/v3/spaces/${spaceId}/anniversaries`,
      anniversaryPayload(payload), { headers: stepHeader(token) }))
    invalidateApiCache('anniversaries:')
    return response
  },
  async uploadCover(file) {
    const spaceId = await activeSpaceId()
    const formData = new FormData()
    formData.append('file', file)
    const response = await request.post(`/api/v3/spaces/${spaceId}/media`, formData, { timeout: 10 * 60 * 1000 })
    const media = normalizeMedia(response.data)
    return { ...response, data: { ...media, coverAssetId: media.assetId } }
  },
  async update(id, payload) {
    const spaceId = await activeSpaceId()
    const response = await withStepUpRetry(token => request.put(`/api/v3/spaces/${spaceId}/anniversaries/${id}`,
      anniversaryPayload(payload), { headers: stepHeader(token) }))
    invalidateApiCache('anniversaries:')
    return response
  },
  async remove(id) {
    const spaceId = await activeSpaceId()
    const response = await request.delete(`/api/v3/spaces/${spaceId}/anniversaries/${id}`)
    invalidateApiCache('anniversaries:')
    return response
  }
}

const mediaPhoto = (media, favorite = false) => {
  const normalized = normalizeMedia(media)
  return {
    assetId: normalized.assetId,
    media: normalized,
    favorite,
    diaryId: null,
    diaryTitle: normalized.caption || normalized.filename || '照片',
    diaryDate: normalized.takenAt?.slice(0, 10) || normalized.createdAt?.slice(0, 10)
  }
}

const mediaPage = async (params = {}) => {
  const spaceId = await activeSpaceId()
  const favoriteOnly = !!params.favoriteOnly
  if (favoriteOnly) {
    const page = Math.max(0, Number(params.page) || 0)
    const size = Math.max(1, Math.min(60, Number(params.size) || 30))
    const response = await request.get(`/api/v3/spaces/${spaceId}/albums/system/favorites`, {
      params: { page, size }, headers: stepHeader(getStepUpToken())
    })
    const content = (response.data.media || []).map(item => mediaPhoto(item, true))
    const total = Number(response.data.totalMedia ?? response.data.album?.mediaCount ?? content.length)
    return { ...response, data: { content, pageNumber: Number(response.data.pageNumber ?? page),
      pageSize: Number(response.data.pageSize ?? size), totalElements: total,
      totalPages: Math.ceil(total / size) } }
  }
  const response = await request.get(`/api/v3/spaces/${spaceId}/media`, {
    params: { mediaType: 'IMAGE', libraryOnly: true, size: Math.min(60, Number(params.size) || 30), cursor: params.cursor },
    headers: stepHeader(getStepUpToken())
  })
  const content = (response.data.items || []).map(item => mediaPhoto(item, false))
  return { ...response, data: { content, pageNumber: Number(params.page) || 0, pageSize: Number(params.size) || 30,
    totalElements: content.length + (response.data.nextCursor ? 1 : 0), totalPages: response.data.nextCursor ? 2 : 1,
    nextCursor: response.data.nextCursor } }
}

export const photoApi = {
  list(params = {}, options = {}) {
    return cachedRequest(`photos:list:${stableStringify(params)}`, async () => {
      const response = await mediaPage({ ...params, size: 60 })
      return { ...response, data: response.data.content }
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  page(params = {}, options = {}) {
    return cachedRequest(`photos:page:${stableStringify(params)}`, () => mediaPage(params),
      { ttl: options.ttl ?? 30000, force: options.force })
  },
  async favorite(assetId) {
    const spaceId = await activeSpaceId()
    const response = await withStepUpRetry(token => request.put(`/api/v3/spaces/${spaceId}/media/${assetId}/favorite`,
      null, { headers: stepHeader(token) }))
    invalidateApiCache('photos:')
    invalidateApiCache('albums:')
    return { ...response, data: { assetId, favorite: true } }
  },
  async unfavorite(assetId) {
    const spaceId = await activeSpaceId()
    const response = await withStepUpRetry(token => request.delete(`/api/v3/spaces/${spaceId}/media/${assetId}/favorite`,
      { headers: stepHeader(token) }))
    invalidateApiCache('photos:')
    invalidateApiCache('albums:')
    return response
  }
}

export const draftApi = {
  list(options = {}) {
    return cachedRequest('drafts:list', async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/drafts`)
      return { ...response, data: (response.data || []).map(normalizeDraft) }
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  get(draftKey, options = {}) {
    return cachedRequest(`drafts:item:${draftKey}`, async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`)
      return { ...response, data: normalizeDraft(response.data) }
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  async save(payload) {
    const spaceId = await activeSpaceId()
    const { draftKey, diaryId, ...draftPayload } = payload
    const response = await request.put(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`, {
      diaryId: diaryId || null, payload: draftPayload
    })
    invalidateApiCache('drafts:')
    return { ...response, data: normalizeDraft(response.data) }
  },
  async remove(draftId) {
    const drafts = await this.list({ force: true })
    const draft = drafts.data.find(item => item.draftId === draftId)
    return draft ? this.removeByKey(draft.draftKey) : { code: 200, data: null }
  },
  async removeByKey(draftKey) {
    const spaceId = await activeSpaceId()
    const response = await request.delete(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`)
    invalidateApiCache('drafts:')
    return response
  }
}

import request from '@/utils/request'
import { normalizeAnniversary, normalizeDraft, normalizeMedia } from '@/api/v3Adapters'
import { invalidateDiaryReads } from '@/api/diary'
import { cachedRequest, invalidateApiCache } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const tagApi = {
  list(spaceId, options = {}) {
    return cachedRequest(`spaces:${spaceId}:tags:list`, () => (
      request.get(`/api/v3/spaces/${spaceId}/tags`)
    ), { ttl: options.ttl ?? 600000, force: options.force })
  },

  async create(spaceId, payload) {
    const tag = await request.post(`/api/v3/spaces/${spaceId}/tags`, payload)
    invalidateApiCache(`spaces:${spaceId}:tags:`)
    invalidateDiaryReads(spaceId)
    return tag
  }
}

const anniversaryPayload = value => ({
  title: value.title,
  date: value.date,
  description: value.description || null,
  coverAssetId: value.coverAssetId || null,
  sortOrder: Number(value.sortOrder) || 0
})

export const anniversaryApi = {
  list(spaceId, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(`spaces:${spaceId}:anniversaries:list:access:${stepUpToken ? 'elevated' : 'standard'}`, async () => {
      const result = await request.get(`/api/v3/spaces/${spaceId}/anniversaries`, {
        headers: stepHeader(stepUpToken)
      })
      return (result || []).map(normalizeAnniversary)
    }, { ttl: options.ttl ?? 600000, force: options.force, cacheIf: () => !stepUpToken })
  },

  async create(spaceId, payload) {
    const item = await withStepUpRetry(token => request.post(
      `/api/v3/spaces/${spaceId}/anniversaries`,
      anniversaryPayload(payload),
      { headers: stepHeader(token) }
    ))
    invalidateApiCache(`spaces:${spaceId}:anniversaries:`)
    return normalizeAnniversary(item)
  },

  async uploadCover(spaceId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return normalizeMedia(await request.post(`/api/v3/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000
    }))
  },

  async update(spaceId, id, payload) {
    const item = await withStepUpRetry(token => request.put(
      `/api/v3/spaces/${spaceId}/anniversaries/${id}`,
      anniversaryPayload(payload),
      { headers: stepHeader(token) }
    ))
    invalidateApiCache(`spaces:${spaceId}:anniversaries:`)
    return normalizeAnniversary(item)
  },

  async remove(spaceId, id) {
    await request.delete(`/api/v3/spaces/${spaceId}/anniversaries/${id}`)
    invalidateApiCache(`spaces:${spaceId}:anniversaries:`)
  }
}

export const draftApi = {
  list(spaceId, options = {}) {
    return cachedRequest(`spaces:${spaceId}:drafts:list`, async () => (
      (await request.get(`/api/v3/spaces/${spaceId}/drafts`) || []).map(normalizeDraft)
    ), { ttl: options.ttl ?? 30000, force: options.force })
  },

  get(spaceId, draftKey, options = {}) {
    return cachedRequest(`spaces:${spaceId}:drafts:item:${draftKey}`, async () => normalizeDraft(
      await request.get(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`)
    ), { ttl: options.ttl ?? 30000, force: options.force })
  },

  async save(spaceId, payload) {
    const { draftKey, diaryId, ...draftPayload } = payload
    const draft = await request.put(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`, {
      diaryId: diaryId || null,
      payload: draftPayload
    })
    invalidateApiCache(`spaces:${spaceId}:drafts:`)
    return normalizeDraft(draft)
  },

  async remove(spaceId, draftKey) {
    await request.delete(`/api/v3/spaces/${spaceId}/drafts/${encodeURIComponent(draftKey)}`)
    invalidateApiCache(`spaces:${spaceId}:drafts:`)
  }
}

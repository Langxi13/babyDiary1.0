import request from '@/utils/request'
import { invalidateDiaryReads } from '@/api/diary'
import { cachedRequest, invalidateApiCache, stableStringify } from '@/utils/apiCache'

export const tagApi = {
  list(options = {}) {
    return cachedRequest(
      'tags:list',
      () => request.get('/api/tags'),
      { ttl: options.ttl ?? 600000, force: options.force }
    )
  },
  async create(payload) {
    const response = await request.post('/api/tags', payload)
    invalidateApiCache('tags:')
    invalidateDiaryReads()
    return response
  }
}

export const anniversaryApi = {
  list(options = {}) {
    return cachedRequest(
      'anniversaries:list',
      () => request.get('/api/anniversaries'),
      { ttl: options.ttl ?? 600000, force: options.force }
    )
  },
  async create(payload) {
    const response = await request.post('/api/anniversaries', payload)
    invalidateApiCache('anniversaries:')
    return response
  },
  async uploadCover(file) {
    const formData = new FormData()
    formData.append('coverFile', file)
    const response = await request.post('/api/anniversaries/cover', formData)
    invalidateApiCache('anniversaries:')
    return response
  },
  async update(id, payload) {
    const response = await request.put(`/api/anniversaries/${id}`, payload)
    invalidateApiCache('anniversaries:')
    return response
  },
  async remove(id) {
    const response = await request.delete(`/api/anniversaries/${id}`)
    invalidateApiCache('anniversaries:')
    return response
  }
}

export const photoApi = {
  list(params = {}, options = {}) {
    return cachedRequest(
      `photos:list:${stableStringify(params)}`,
      () => request.get('/api/photos', { params }),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  page(params = {}, options = {}) {
    return cachedRequest(
      `photos:page:${stableStringify(params)}`,
      () => request.get('/api/photos/page', { params }),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  async favorite(imageId) {
    const response = await request.post(`/api/photos/${imageId}/favorite`)
    invalidateApiCache('photos:')
    invalidateApiCache('albums:')
    return response
  },
  async unfavorite(imageId) {
    const response = await request.delete(`/api/photos/${imageId}/favorite`)
    invalidateApiCache('photos:')
    invalidateApiCache('albums:')
    return response
  }
}

export const draftApi = {
  list(options = {}) {
    return cachedRequest(
      'drafts:list',
      () => request.get('/api/diary-drafts'),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  get(draftKey, options = {}) {
    return cachedRequest(
      `drafts:item:${draftKey}`,
      () => request.get(`/api/diary-drafts/${draftKey}`),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  async save(payload) {
    const response = await request.put('/api/diary-drafts', payload)
    invalidateApiCache('drafts:')
    return response
  },
  async remove(draftId) {
    const response = await request.delete(`/api/diary-drafts/${draftId}`)
    invalidateApiCache('drafts:')
    return response
  },
  async removeByKey(draftKey) {
    const response = await request.delete(`/api/diary-drafts/key/${draftKey}`)
    invalidateApiCache('drafts:')
    return response
  }
}

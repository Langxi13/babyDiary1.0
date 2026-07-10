import request from '@/utils/request'
import { invalidateApiCache, cachedRequest, stableStringify } from '@/utils/apiCache'

const systemPhotosUrl = (systemKey) => systemKey === 'all'
  ? '/api/albums/system/all/photos'
  : systemKey === 'favorites'
    ? '/api/albums/system/favorites/photos'
    : `/api/albums/system/year/${String(systemKey).replace('year:', '')}/photos`

export const albumApi = {
  getGroups(options = {}) {
    return cachedRequest(
      'albums:groups',
      () => request.get('/api/albums/groups'),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  getSystemPhotos(systemKey, options = {}) {
    const url = systemPhotosUrl(systemKey)
    return cachedRequest(
      `albums:system:${systemKey}`,
      () => request.get(url),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  getSystemPhotoPage(systemKey, params = {}, options = {}) {
    return cachedRequest(
      `albums:system:${systemKey}:page:${stableStringify(params)}`,
      () => request.get(`${systemPhotosUrl(systemKey)}/page`, { params }),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  getAlbumPhotos(albumId, options = {}) {
    return cachedRequest(
      `albums:${albumId}:photos`,
      () => request.get(`/api/albums/${albumId}/photos`),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  getAlbumPhotoPage(albumId, params = {}, options = {}) {
    return cachedRequest(
      `albums:${albumId}:photos:page:${stableStringify(params)}`,
      () => request.get(`/api/albums/${albumId}/photos/page`, { params }),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  },
  async createGroup(payload) {
    const response = await request.post('/api/albums/groups', payload)
    invalidateApiCache('albums:')
    return response
  },
  async updateGroup(groupId, payload) {
    const response = await request.put(`/api/albums/groups/${groupId}`, payload)
    invalidateApiCache('albums:')
    return response
  },
  async deleteGroup(groupId) {
    const response = await request.delete(`/api/albums/groups/${groupId}`)
    invalidateApiCache('albums:')
    return response
  },
  async createAlbum(payload) {
    const response = await request.post('/api/albums', payload)
    invalidateApiCache('albums:')
    return response
  },
  async updateAlbum(albumId, payload) {
    const response = await request.put(`/api/albums/${albumId}`, payload)
    invalidateApiCache('albums:')
    return response
  },
  async deleteAlbum(albumId) {
    const response = await request.delete(`/api/albums/${albumId}`)
    invalidateApiCache('albums:')
    return response
  },
  async removeAlbumPhoto(albumId, imageId) {
    const response = await request.delete(`/api/albums/${albumId}/photos/${imageId}`)
    invalidateApiCache('albums:')
    return response
  },
  generateProposal(payload) {
    return request.post('/api/ai/albums/proposals', payload)
  },
  updateProposal(proposalId, payload) {
    return request.put(`/api/ai/albums/proposals/${proposalId}`, payload)
  },
  confirmProposal(proposalId) {
    return request.post(`/api/ai/albums/proposals/${proposalId}/confirm`)
  },
  discardProposal(proposalId) {
    return request.delete(`/api/ai/albums/proposals/${proposalId}`)
  },
  getProposal(proposalId, options = {}) {
    return cachedRequest(
      `albums:proposal:${proposalId}:${stableStringify({})}`,
      () => request.get(`/api/ai/albums/proposals/${proposalId}`),
      { ttl: options.ttl ?? 30000, force: options.force }
    )
  }
}

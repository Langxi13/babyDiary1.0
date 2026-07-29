import request from '@/utils/request'
import { activeSpaceId, normalizeAlbum, normalizeAlbumGroup, normalizeMedia } from '@/api/v3Adapters'
import { invalidateApiCache, cachedRequest, stableStringify } from '@/utils/apiCache'

const photo = (media, favorite = false) => {
  const value = normalizeMedia(media)
  return {
    assetId: value.assetId,
    media: value,
    favorite,
    diaryId: null,
    diaryTitle: value.caption || value.filename || '照片',
    diaryDate: value.takenAt?.slice(0, 10) || value.createdAt?.slice(0, 10)
  }
}

const pageResult = (response, items, page, size, total, nextCursor = null) => ({
  ...response,
  data: { content: items, pageNumber: page, pageSize: size, totalElements: total,
    totalPages: Math.ceil(total / size), nextCursor }
})

const albumDetailPage = async (path, params, favorite = false) => {
  const page = Math.max(0, Number(params.page) || 0)
  const size = Math.max(1, Math.min(60, Number(params.size) || 24))
  const response = await request.get(path, { params: { page, size } })
  const all = (response.data.media || []).map(item => photo(item, favorite))
  const total = Number(response.data.totalMedia ?? response.data.album?.mediaCount ?? all.length)
  return pageResult(response, all, Number(response.data.pageNumber ?? page),
    Number(response.data.pageSize ?? size), total)
}

export const albumApi = {
  getGroups(options = {}) {
    return cachedRequest('albums:groups', async () => {
      const spaceId = await activeSpaceId()
      const response = await request.get(`/api/v3/spaces/${spaceId}/album-groups`)
      return { ...response, data: (response.data.groups || []).map(normalizeAlbumGroup) }
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  getSystemPhotoPage(systemKey, params = {}, options = {}) {
    return cachedRequest(`albums:system:${systemKey}:page:${stableStringify(params)}`, async () => {
      const spaceId = await activeSpaceId()
      if (systemKey !== 'all' && systemKey !== 'favorites') {
        return pageResult({ code: 200 }, [], 0, Number(params.size) || 24, 0)
      }
      return albumDetailPage(`/api/v3/spaces/${spaceId}/albums/system/${systemKey}`, params, systemKey === 'favorites')
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  getAlbumPhotoPage(albumId, params = {}, options = {}) {
    return cachedRequest(`albums:${albumId}:photos:page:${stableStringify(params)}`, async () => {
      const spaceId = await activeSpaceId()
      return albumDetailPage(`/api/v3/spaces/${spaceId}/albums/${albumId}`, params, false)
    }, { ttl: options.ttl ?? 30000, force: options.force })
  },
  async createGroup(payload) {
    const spaceId = await activeSpaceId()
    const response = await request.post(`/api/v3/spaces/${spaceId}/album-groups`, payload)
    invalidateApiCache('albums:')
    return response
  },
  async updateGroup(groupId, payload) {
    const spaceId = await activeSpaceId()
    const response = await request.put(`/api/v3/spaces/${spaceId}/album-groups/${groupId}`, payload)
    invalidateApiCache('albums:')
    return response
  },
  async deleteGroup(groupId) {
    const spaceId = await activeSpaceId()
    const response = await request.delete(`/api/v3/spaces/${spaceId}/album-groups/${groupId}`)
    invalidateApiCache('albums:')
    return response
  },
  async createAlbum(payload) {
    const spaceId = await activeSpaceId()
    const response = await request.post(`/api/v3/spaces/${spaceId}/albums`, { ...payload, mediaIds: payload.mediaIds || [] })
    invalidateApiCache('albums:')
    return { ...response, data: normalizeAlbum(response.data) }
  },
  async updateAlbum(albumId, payload) {
    const spaceId = await activeSpaceId()
    const response = await request.put(`/api/v3/spaces/${spaceId}/albums/${albumId}`, payload)
    invalidateApiCache('albums:')
    return { ...response, data: normalizeAlbum(response.data) }
  },
  async deleteAlbum(albumId) {
    const spaceId = await activeSpaceId()
    const response = await request.delete(`/api/v3/spaces/${spaceId}/albums/${albumId}`)
    invalidateApiCache('albums:')
    return response
  },
  async removeAlbumPhoto(albumId, assetId) {
    const spaceId = await activeSpaceId()
    const response = await request.delete(`/api/v3/spaces/${spaceId}/albums/${albumId}/media/${assetId}`)
    invalidateApiCache('albums:')
    return response
  },
  async generateProposal(payload) {
    const spaceId = await activeSpaceId()
    return request.post(`/api/v3/spaces/${spaceId}/ai-album-proposals`, payload, { timeout: 120000 })
  },
  async updateProposal(proposalId, payload) {
    const spaceId = await activeSpaceId()
    return request.put(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`, payload)
  },
  async confirmProposal(proposalId) {
    const spaceId = await activeSpaceId()
    const response = await request.post(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}/confirm`)
    invalidateApiCache('albums:')
    return response
  },
  async discardProposal(proposalId) {
    const spaceId = await activeSpaceId()
    return request.delete(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`)
  },
  getProposal(proposalId, options = {}) {
    return cachedRequest(`albums:proposal:${proposalId}`, async () => {
      const spaceId = await activeSpaceId()
      return request.get(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`)
    }, { ttl: options.ttl ?? 30000, force: options.force })
  }
}

import request from '@/utils/request'
import { normalizeAlbum, normalizeAlbumGroup, normalizeMedia } from '@/api/v3Adapters'
import { invalidateApiCache, cachedRequest, stableStringify } from '@/utils/apiCache'
import { getStepUpToken, withStepUpRetry } from '@/utils/stepUp'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

const photo = (media, favorite = false) => {
  const value = normalizeMedia(media)
  return {
    id: value.id,
    media: value,
    favorite,
    title: value.caption || value.originalFilename || '照片',
    date: value.takenAt?.slice(0, 10) || value.createdAt?.slice(0, 10)
  }
}

const pageResult = (content, page, size, total, nextCursor = null) => ({
  content,
  pageNumber: page,
  pageSize: size,
  totalElements: total,
  totalPages: Math.ceil(total / size),
  nextCursor
})

const albumDetailPage = async (path, params, favorite = false, stepUpToken = '') => {
  const page = Math.max(0, Number(params.page) || 0)
  const size = Math.max(1, Math.min(60, Number(params.size) || 24))
  const result = await request.get(path, {
    params: { page, size }, headers: stepHeader(stepUpToken)
  })
  const content = (result.media || []).map(item => photo(item, favorite))
  const total = Number(result.totalMedia ?? result.album?.mediaCount ?? content.length)
  return pageResult(
    content,
    Number(result.pageNumber ?? page),
    Number(result.pageSize ?? size),
    total
  )
}

const normalizeProposal = proposal => proposal ? {
  ...proposal,
  albums: (proposal.albums || []).map(album => ({
    ...album,
    photos: (album.photos || []).map(normalizeMedia)
  }))
} : proposal

export const albumApi = {
  getGroups(spaceId, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(`spaces:${spaceId}:albums:groups:access:${accessMode(stepUpToken)}`, async () => {
      const result = await request.get(`/api/v3/spaces/${spaceId}/album-groups`, {
        headers: stepHeader(stepUpToken)
      })
      return (result.groups || []).map(normalizeAlbumGroup)
    }, { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => !stepUpToken })
  },

  getSystemPhotoPage(spaceId, systemKey, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(`spaces:${spaceId}:albums:system:${systemKey}:page:${stableStringify(params)}:access:${accessMode(stepUpToken)}`, async () => {
      if (!['all', 'favorites'].includes(systemKey) && !/^year:[0-9]{4}$/.test(systemKey)) {
        return pageResult([], 0, Number(params.size) || 24, 0)
      }
      return albumDetailPage(
        `/api/v3/spaces/${spaceId}/albums/system/${systemKey}`,
        params,
        systemKey === 'favorites',
        stepUpToken
      )
    }, { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => !stepUpToken })
  },

  getAlbumPhotoPage(spaceId, albumId, params = {}, options = {}) {
    const stepUpToken = getStepUpToken()
    return cachedRequest(`spaces:${spaceId}:albums:${albumId}:photos:page:${stableStringify(params)}:access:${accessMode(stepUpToken)}`, () => (
      albumDetailPage(`/api/v3/spaces/${spaceId}/albums/${albumId}`, params, false, stepUpToken)
    ), { ttl: options.ttl ?? 30000, force: options.force, cacheIf: () => !stepUpToken })
  },

  async createGroup(spaceId, payload) {
    const group = await request.post(`/api/v3/spaces/${spaceId}/album-groups`, payload)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return group
  },

  async updateGroup(spaceId, groupId, payload) {
    const group = await request.put(`/api/v3/spaces/${spaceId}/album-groups/${groupId}`, payload)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return group
  },

  async deleteGroup(spaceId, groupId) {
    await request.delete(`/api/v3/spaces/${spaceId}/album-groups/${groupId}`)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
  },

  async createAlbum(spaceId, payload) {
    const album = await withStepUpRetry(token => request.post(`/api/v3/spaces/${spaceId}/albums`, {
      ...payload,
      mediaIds: payload.mediaIds || []
    }, { headers: stepHeader(token) }))
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return normalizeAlbum(album)
  },

  async updateAlbum(spaceId, albumId, payload) {
    const album = await request.put(`/api/v3/spaces/${spaceId}/albums/${albumId}`, payload)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return normalizeAlbum(album)
  },

  async deleteAlbum(spaceId, albumId) {
    await request.delete(`/api/v3/spaces/${spaceId}/albums/${albumId}`)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
  },

  async removeAlbumPhoto(spaceId, albumId, mediaId) {
    await withStepUpRetry(token => request.delete(
      `/api/v3/spaces/${spaceId}/albums/${albumId}/media/${mediaId}`,
      { headers: stepHeader(token) }
    ))
    invalidateApiCache(`spaces:${spaceId}:albums:`)
  },

  async favoriteMedia(spaceId, mediaId) {
    await withStepUpRetry(token => request.put(
      `/api/v3/spaces/${spaceId}/media/${mediaId}/favorite`,
      null,
      { headers: stepHeader(token) }
    ))
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return { id: mediaId, favorite: true }
  },

  async unfavoriteMedia(spaceId, mediaId) {
    await withStepUpRetry(token => request.delete(
      `/api/v3/spaces/${spaceId}/media/${mediaId}/favorite`,
      { headers: stepHeader(token) }
    ))
    invalidateApiCache(`spaces:${spaceId}:albums:`)
  },

  async generateProposal(spaceId, payload) {
    return normalizeProposal(await request.post(
      `/api/v3/spaces/${spaceId}/ai-album-proposals`, payload, { timeout: 120000 }
    ))
  },

  async updateProposal(spaceId, proposalId, payload) {
    return normalizeProposal(await request.put(
      `/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`, payload
    ))
  },

  async confirmProposal(spaceId, proposalId) {
    const result = await request.post(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}/confirm`)
    invalidateApiCache(`spaces:${spaceId}:albums:`)
    return result
  },

  discardProposal(spaceId, proposalId) {
    return request.delete(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`)
  },

  getProposal(spaceId, proposalId, options = {}) {
    return cachedRequest(`spaces:${spaceId}:albums:proposal:${proposalId}`, async () => normalizeProposal(
      await request.get(`/api/v3/spaces/${spaceId}/ai-album-proposals/${proposalId}`)
    ), { ttl: options.ttl ?? 30000, force: options.force })
  }
}

const accessMode = token => token ? 'elevated' : 'standard'

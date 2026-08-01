import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { normalizeMedia } from '@/api/models'
import { isNativeApp } from '@/platform/runtimeConfig'
import { uploadNativeMedia } from '@/platform/nativeFiles'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const mediaApi = {
  async upload(spaceId, formData) {
    return normalizeMedia(await request.post(`${API_ROOT}/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000
    }))
  },

  async uploadSource(spaceId, source, metadata = {}) {
    if (isNativeApp() && source?.kind === 'native-uri') {
      return normalizeMedia(await uploadNativeMedia(spaceId, source, metadata))
    }
    const file = source?.file || source
    const formData = new FormData()
    formData.append('file', file, file.name || 'media')
    if (metadata.caption) formData.append('caption', metadata.caption)
    if (metadata.takenAt) formData.append('takenAt', metadata.takenAt)
    return normalizeMedia(await request.post(`${API_ROOT}/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000,
      headers: { 'Idempotency-Key': source?.uploadId || crypto.randomUUID() }
    }))
  },

  update(spaceId, mediaId, data, stepUpToken) {
    return request.put(`${API_ROOT}/spaces/${spaceId}/media/${mediaId}`, data, {
      headers: stepHeader(stepUpToken)
    })
  },

  remove(spaceId, mediaId) {
    return request.delete(`${API_ROOT}/spaces/${spaceId}/media/${mediaId}`)
  }
}

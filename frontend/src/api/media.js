import request from '@/utils/request'
import { normalizeMedia } from '@/api/v3Adapters'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const mediaApi = {
  async upload(spaceId, formData) {
    return normalizeMedia(await request.post(`/api/v3/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000
    }))
  },

  update(spaceId, mediaId, data, stepUpToken) {
    return request.put(`/api/v3/spaces/${spaceId}/media/${mediaId}`, data, {
      headers: stepHeader(stepUpToken)
    })
  },

  remove(spaceId, mediaId) {
    return request.delete(`/api/v3/spaces/${spaceId}/media/${mediaId}`)
  }
}

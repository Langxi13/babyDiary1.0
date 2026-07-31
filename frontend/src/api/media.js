import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { normalizeMedia } from '@/api/models'

const stepHeader = token => token ? { 'X-Step-Up-Token': token } : {}

export const mediaApi = {
  async upload(spaceId, formData) {
    return normalizeMedia(await request.post(`${API_ROOT}/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000
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

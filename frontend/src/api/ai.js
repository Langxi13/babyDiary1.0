import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'

const aiRequestTimeout = timeoutSeconds => (
  Math.max(5, Math.min(Number(timeoutSeconds) || 30, 120)) + 10
) * 1000

export const aiApi = {
  getConfig() {
    return request.get(`${API_ROOT}/admin/ai`)
  },

  saveConfig(payload) {
    return request.post(`${API_ROOT}/admin/ai`, payload)
  },

  testConfig(timeoutSeconds) {
    return request.post(`${API_ROOT}/admin/ai/test`, null, {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  listModels(timeoutSeconds) {
    return request.get(`${API_ROOT}/admin/ai/models`, {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  generateReport(spaceId, payload, timeoutSeconds) {
    return request.post(`${API_ROOT}/spaces/${spaceId}/ai-reports`, payload, {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  listReports(spaceId, params = {}) {
    return request.get(`${API_ROOT}/spaces/${spaceId}/ai-reports`, { params })
  },

  getReport(spaceId, reportId) {
    return request.get(`${API_ROOT}/spaces/${spaceId}/ai-reports/${reportId}`)
  },

  deleteReport(spaceId, reportId) {
    return request.delete(`${API_ROOT}/spaces/${spaceId}/ai-reports/${reportId}`)
  },

  getSchedule(spaceId) {
    return request.get(`${API_ROOT}/spaces/${spaceId}/ai/schedule`)
  },

  updateSchedule(spaceId, data) {
    return request.put(`${API_ROOT}/spaces/${spaceId}/ai/schedule`, data)
  }
}

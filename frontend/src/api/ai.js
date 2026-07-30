import request from '@/utils/request'

const aiRequestTimeout = timeoutSeconds => (
  Math.max(5, Math.min(Number(timeoutSeconds) || 30, 120)) + 10
) * 1000

export const aiApi = {
  getConfig() {
    return request.get('/api/v3/admin/ai')
  },

  saveConfig(payload) {
    return request.post('/api/v3/admin/ai', payload)
  },

  testConfig(timeoutSeconds) {
    return request.post('/api/v3/admin/ai/test', null, {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  listModels(timeoutSeconds) {
    return request.get('/api/v3/admin/ai/models', {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  generateReport(spaceId, payload, timeoutSeconds) {
    return request.post(`/api/v3/spaces/${spaceId}/ai-reports`, payload, {
      timeout: aiRequestTimeout(timeoutSeconds)
    })
  },

  async listReports(spaceId, params = {}) {
    const reports = await request.get(`/api/v3/spaces/${spaceId}/ai-reports`) || []
    const filtered = reports.filter(report => !params.type || report.periodType === params.type)
    const pageNumber = Math.max(0, Number(params.page) || 0)
    const pageSize = Math.max(1, Number(params.size) || 10)
    return {
      content: filtered.slice(pageNumber * pageSize, (pageNumber + 1) * pageSize),
      pageNumber,
      pageSize,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / pageSize)
    }
  },

  getReport(spaceId, reportId) {
    return request.get(`/api/v3/spaces/${spaceId}/ai-reports/${reportId}`)
  },

  deleteReport(spaceId, reportId) {
    return request.delete(`/api/v3/spaces/${spaceId}/ai-reports/${reportId}`)
  },

  getSchedule(spaceId) {
    return request.get(`/api/v3/spaces/${spaceId}/ai/schedule`)
  },

  updateSchedule(spaceId, data) {
    return request.put(`/api/v3/spaces/${spaceId}/ai/schedule`, data)
  }
}

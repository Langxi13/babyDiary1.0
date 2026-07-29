import request from '@/utils/request'
import { activeSpaceId } from '@/api/v3Adapters'

const aiRequestTimeout = timeoutSeconds => (Math.max(5, Math.min(Number(timeoutSeconds) || 30, 120)) + 10) * 1000
const normalizeReport = report => ({ ...report, reportId: report.id, type: report.periodType,
  startDate: report.start, endDate: report.end })

export const aiApi = {
  async getConfig() {
    const response = await request.get('/api/v3/admin/ai')
    return { ...response, data: { ...response.data, apiKeyMasked: response.data.hasApiKey ? '********' : '' } }
  },
  async saveConfig(payload) {
    const response = await request.post('/api/v3/admin/ai', payload)
    return { ...response, data: { ...response.data, apiKeyMasked: response.data.hasApiKey ? '********' : '' } }
  },
  testConfig(timeoutSeconds) {
    return request.post('/api/v3/admin/ai/test', null, { timeout: aiRequestTimeout(timeoutSeconds) })
  },
  listModels(timeoutSeconds) {
    return request.get('/api/v3/admin/ai/models', { timeout: aiRequestTimeout(timeoutSeconds) })
  },
  async generateReport(payload, timeoutSeconds) {
    const spaceId = await activeSpaceId()
    const response = await request.post(`/api/v3/spaces/${spaceId}/ai-reports`, payload, { timeout: aiRequestTimeout(timeoutSeconds) })
    return { ...response, data: normalizeReport(response.data) }
  },
  async listReports(params = {}) {
    const spaceId = await activeSpaceId()
    const response = await request.get(`/api/v3/spaces/${spaceId}/ai-reports`)
    const filtered = (response.data || []).map(normalizeReport)
      .filter(report => !params.type || report.periodType === params.type)
    const page = Math.max(0, Number(params.page) || 0)
    const size = Math.max(1, Number(params.size) || 10)
    return { ...response, data: { content: filtered.slice(page * size, (page + 1) * size),
      pageNumber: page, pageSize: size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) } }
  },
  async getReport(reportId) {
    const spaceId = await activeSpaceId()
    const response = await request.get(`/api/v3/spaces/${spaceId}/ai-reports/${reportId}`)
    return { ...response, data: normalizeReport(response.data) }
  },
  async deleteReport(reportId) {
    const spaceId = await activeSpaceId()
    return request.delete(`/api/v3/spaces/${spaceId}/ai-reports/${reportId}`)
  }
}

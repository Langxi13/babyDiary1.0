import request from '@/utils/request'

const aiRequestTimeout = (timeoutSeconds) => {
  const seconds = Math.max(5, Math.min(Number(timeoutSeconds) || 30, 120))
  return (seconds + 10) * 1000
}

export const aiApi = {
  getConfig() {
    return request.get('/api/ai/config')
  },
  saveConfig(payload) {
    return request.put('/api/ai/config', payload)
  },
  testConfig(timeoutSeconds) {
    return request.post('/api/ai/config/test', null, { timeout: aiRequestTimeout(timeoutSeconds) })
  },
  listModels(timeoutSeconds) {
    return request.get('/api/ai/models', { timeout: aiRequestTimeout(timeoutSeconds) })
  },
  generateReport(payload, timeoutSeconds) {
    return request.post('/api/ai/reports/generate', payload, { timeout: aiRequestTimeout(timeoutSeconds) })
  },
  listReports(params) {
    return request.get('/api/ai/reports', { params })
  },
  getReport(reportId) {
    return request.get(`/api/ai/reports/${reportId}`)
  },
  deleteReport(reportId) {
    return request.delete(`/api/ai/reports/${reportId}`)
  }
}

import request from '@/utils/request'

export const workspaceApi = {
  spaces: {
    list: () => request.get('/api/v2/spaces'),
    create: data => request.post('/api/v2/spaces', data),
    rename: (spaceId, data) => request.put(`/api/v2/spaces/${spaceId}`, data),
    members: spaceId => request.get(`/api/v2/spaces/${spaceId}/members`),
    invite: (spaceId, data) => request.post(`/api/v2/spaces/${spaceId}/invitations`, data),
    accept: token => request.post(`/api/v2/spaces/invitations/${token}/accept`),
    removeMember: (spaceId, userId) => request.delete(`/api/v2/spaces/${spaceId}/members/${userId}`),
    updateRole: (spaceId, userId, role) => request.put(`/api/v2/spaces/${spaceId}/members/${userId}/role`, null, { params: { role } }),
    tags: spaceId => request.get(`/api/v2/spaces/${spaceId}/tags`),
    createTag: (spaceId, data) => request.post(`/api/v2/spaces/${spaceId}/tags`, data)
  },
  diaries: {
    list: (spaceId, params) => request.get(`/api/v2/spaces/${spaceId}/diaries`, { params }),
    get: (spaceId, diaryId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/diaries/${diaryId}`, { headers: stepHeader(stepUpToken) }),
    create: (spaceId, data, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/diaries`, data, { headers: stepHeader(stepUpToken) }),
    update: (spaceId, diaryId, data, stepUpToken) => request.put(`/api/v2/spaces/${spaceId}/diaries/${diaryId}`, data, { headers: stepHeader(stepUpToken) }),
    remove: (spaceId, diaryId, version, stepUpToken) => request.delete(`/api/v2/spaces/${spaceId}/diaries/${diaryId}`, { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }),
    restore: (spaceId, diaryId, version, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/restore`, null, { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }),
    revisions: (spaceId, diaryId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/revisions`, { headers: stepHeader(stepUpToken) }),
    restoreRevision: (spaceId, diaryId, revisionId, version, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/revisions/${revisionId}/restore`, null, { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }),
    comments: (spaceId, diaryId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/comments`, { headers: stepHeader(stepUpToken) }),
    addComment: (spaceId, diaryId, content, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/comments`, { content }, { headers: stepHeader(stepUpToken) }),
    updateComment: (spaceId, diaryId, commentId, content, stepUpToken) => request.put(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/comments/${commentId}`, { content }, { headers: stepHeader(stepUpToken) }),
    removeComment: (spaceId, diaryId, commentId, stepUpToken) => request.delete(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/comments/${commentId}`, { headers: stepHeader(stepUpToken) }),
    reactions: (spaceId, diaryId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/reactions`, { headers: stepHeader(stepUpToken) }),
    setReaction: (spaceId, diaryId, emoji, active, stepUpToken) => request.put(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/reactions`, { emoji, active }, { headers: stepHeader(stepUpToken) })
  },
  media: {
    upload: (spaceId, formData, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/media`, formData, {
      headers: stepHeader(stepUpToken),
      timeout: 10 * 60 * 1000
    }),
    update: (spaceId, assetId, data, stepUpToken) => request.put(`/api/v2/spaces/${spaceId}/media/${assetId}`, data, { headers: stepHeader(stepUpToken) }),
    remove: (spaceId, assetId, stepUpToken) => request.delete(`/api/v2/spaces/${spaceId}/media/${assetId}`, { headers: stepHeader(stepUpToken) })
  },
  search: (spaceId, query, limit = 30) => request.get(`/api/v2/spaces/${spaceId}/search`, { params: { query, limit } }),
  insights: (spaceId, year) => request.get(`/api/v2/spaces/${spaceId}/insights/yearly`, { params: { year } }),
  templates: {
    list: spaceId => request.get(`/api/v2/spaces/${spaceId}/templates`),
    create: (spaceId, data) => request.post(`/api/v2/spaces/${spaceId}/templates`, data),
    update: (spaceId, templateId, data) => request.put(`/api/v2/spaces/${spaceId}/templates/${templateId}`, data),
    remove: (spaceId, templateId) => request.delete(`/api/v2/spaces/${spaceId}/templates/${templateId}`)
  },
  sync: {
    pull: (spaceId, cursor = 0, limit = 200) => request.get(`/api/v2/spaces/${spaceId}/sync/pull`, { params: { cursor, limit } }),
    push: (spaceId, operations, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/sync/push`, { operations }, { headers: stepHeader(stepUpToken) })
  },
  notifications: {
    list: params => request.get('/api/v2/notifications', { params }),
    unread: () => request.get('/api/v2/notifications/unread-count'),
    read: id => request.put(`/api/v2/notifications/${id}/read`),
    readAll: () => request.put('/api/v2/notifications/read-all'),
    publicKey: () => request.get('/api/v2/notifications/push/public-key'),
    subscribe: data => request.post('/api/v2/notifications/push/subscriptions', data),
    unsubscribe: endpoint => request.delete('/api/v2/notifications/push/subscriptions', { data: { endpoint } })
  },
  reminders: {
    list: spaceId => request.get(`/api/v2/spaces/${spaceId}/reminders`),
    save: (spaceId, type, data) => request.put(`/api/v2/spaces/${spaceId}/reminders/${type}`, data)
  },
  transfer: {
    exportSpace: (spaceId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/transfer/export`, { responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }),
    importSpace: (spaceId, formData, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/transfer/import`, formData, { headers: stepHeader(stepUpToken), timeout: 10 * 60 * 1000 }),
    exportBook: (spaceId, params, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/books`, { params, responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 })
  },
  shares: {
    create: (spaceId, diaryId, data, stepUpToken) => request.post(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/shares`, data, { headers: stepHeader(stepUpToken) }),
    list: (spaceId, diaryId, stepUpToken) => request.get(`/api/v2/spaces/${spaceId}/diaries/${diaryId}/shares`, { headers: stepHeader(stepUpToken) }),
    open: (token, password) => request.post(`/api/v2/public/shares/${token}/open`, { password }),
    revoke: shareId => request.delete(`/api/v2/shares/${shareId}`)
  },
  ai: {
    reports: (spaceId, params) => request.get(`/api/v2/spaces/${spaceId}/ai/reports`, { params }),
    report: (spaceId, reportId) => request.get(`/api/v2/spaces/${spaceId}/ai/reports/${reportId}`),
    generate: (spaceId, data) => request.post(`/api/v2/spaces/${spaceId}/ai/reports`, data, { timeout: 120000 }),
    schedule: spaceId => request.get(`/api/v2/spaces/${spaceId}/ai/schedule`),
    updateSchedule: (spaceId, data) => request.put(`/api/v2/spaces/${spaceId}/ai/schedule`, data)
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

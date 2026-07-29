import request from '@/utils/request'
import { diaryPayload, normalizeDiary, normalizeMedia, normalizeSpace, normalizeTag } from '@/api/v3Adapters'

export const workspaceApi = {
  spaces: {
    list: async () => {
      const response = await request.get('/api/v3/spaces')
      return { ...response, data: (response.data || []).map(normalizeSpace) }
    },
    create: async data => {
      const response = await request.post('/api/v3/spaces', { ...data, defaultVisibility: data.defaultVisibility || 'SHARED' })
      return { ...response, data: normalizeSpace(response.data) }
    },
    rename: (spaceId, data) => request.put(`/api/v3/spaces/${spaceId}`, data),
    members: spaceId => request.get(`/api/v3/spaces/${spaceId}/members`),
    invite: (spaceId, data) => request.post(`/api/v3/spaces/${spaceId}/invitations`, data),
    accept: token => request.post(`/api/v3/invitations/${token}/accept`),
    removeMember: (spaceId, userId) => request.delete(`/api/v3/spaces/${spaceId}/members/${userId}`),
    updateRole: (spaceId, userId, role) => request.put(`/api/v3/spaces/${spaceId}/members/${userId}/role`, { role }),
    tags: async spaceId => { const response = await request.get(`/api/v3/spaces/${spaceId}/tags`); return { ...response, data: (response.data || []).map(normalizeTag) } },
    createTag: async (spaceId, data) => { const response = await request.post(`/api/v3/spaces/${spaceId}/tags`, data); return { ...response, data: normalizeTag(response.data) } }
  },
  diaries: {
    list: async (spaceId, params) => { const response = await request.get(`/api/v3/spaces/${spaceId}/diaries`, { params }); return { ...response, data: { content: (response.data.items || []).map(normalizeDiary), nextCursor: response.data.nextCursor, totalElements: response.data.totalElements } } },
    get: async (spaceId, diaryId) => { const response = await request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}`); return { ...response, data: normalizeDiary(response.data) } },
    create: async (spaceId, data) => { const response = await request.post(`/api/v3/spaces/${spaceId}/diaries`, diaryPayload(data)); return { ...response, data: normalizeDiary(response.data) } },
    update: async (spaceId, diaryId, data) => { const response = await request.put(`/api/v3/spaces/${spaceId}/diaries/${diaryId}`, diaryPayload(data), { headers: { 'If-Match': `"${data.version ?? data.baseVersion}"` } }); return { ...response, data: normalizeDiary(response.data) } },
    remove: (spaceId, diaryId, version, stepUpToken) => request.delete(`/api/v3/spaces/${spaceId}/diaries/${diaryId}`, { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }),
    restore: (spaceId, diaryId, version, stepUpToken) => request.post(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/restore`, null, { headers: { ...stepHeader(stepUpToken), 'If-Match': `"${version}"` } }),
    revisions: (spaceId, diaryId) => request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/revisions`),
    restoreRevision: (spaceId, diaryId, revisionId, version) => request.post(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/revisions/${revisionId}/restore`, null, { headers: { 'If-Match': `"${version}"` } }),
    comments: (spaceId, diaryId, stepUpToken) => request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/comments`, { headers: stepHeader(stepUpToken) }),
    addComment: (spaceId, diaryId, content, stepUpToken) => request.post(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/comments`, { content }, { headers: stepHeader(stepUpToken) }),
    updateComment: (spaceId, diaryId, commentId, content, stepUpToken) => request.put(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/comments/${commentId}`, { content }, { headers: stepHeader(stepUpToken) }),
    removeComment: (spaceId, diaryId, commentId, stepUpToken) => request.delete(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/comments/${commentId}`, { headers: stepHeader(stepUpToken) }),
    reactions: (spaceId, diaryId, stepUpToken) => request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/reactions`, { headers: stepHeader(stepUpToken) }),
    setReaction: (spaceId, diaryId, emoji, active, stepUpToken) => request.put(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/reactions`, { emoji, active }, { headers: stepHeader(stepUpToken) })
  },
  media: {
    upload: async (spaceId, formData) => {
      const response = await request.post(`/api/v3/spaces/${spaceId}/media`, formData, {
      timeout: 10 * 60 * 1000
      })
      return { ...response, data: normalizeMedia(response.data) }
    },
    update: (spaceId, assetId, data, stepUpToken) => request.put(`/api/v3/spaces/${spaceId}/media/${assetId}`, data, { headers: stepHeader(stepUpToken) }),
    remove: (spaceId, assetId) => request.delete(`/api/v3/spaces/${spaceId}/media/${assetId}`)
  },
  search: (spaceId, query, limit = 30) => request.get(`/api/v3/spaces/${spaceId}/search`, { params: { query, limit } }),
  insights: (spaceId, year) => request.get(`/api/v3/spaces/${spaceId}/insights/yearly`, { params: { year } }),
  templates: {
    list: spaceId => request.get(`/api/v3/spaces/${spaceId}/templates`),
    create: (spaceId, data) => request.post(`/api/v3/spaces/${spaceId}/templates`, data),
    update: (spaceId, templateId, data) => request.put(`/api/v3/spaces/${spaceId}/templates/${templateId}`, data),
    remove: (spaceId, templateId) => request.delete(`/api/v3/spaces/${spaceId}/templates/${templateId}`)
  },
  sync: {
    pull: (spaceId, cursor = 0, limit = 200) => request.get(`/api/v3/spaces/${spaceId}/sync/pull`, { params: { cursor, limit } }),
    push: (spaceId, operations, stepUpToken) => request.post(`/api/v3/spaces/${spaceId}/sync/push`, { operations }, { headers: stepHeader(stepUpToken) })
  },
  notifications: {
    list: params => request.get('/api/v3/notifications', { params }),
    unread: async () => { const response = await request.get('/api/v3/notifications/unread-count'); return { ...response, data: response.data.count } },
    read: id => request.put(`/api/v3/notifications/${id}/read`),
    readAll: () => request.put('/api/v3/notifications/read-all'),
    publicKey: async () => { const response = await request.get('/api/v3/notifications/push/public-key'); return { ...response, data: response.data.publicKey } },
    subscribe: data => request.post('/api/v3/notifications/push/subscriptions', data),
    unsubscribe: endpoint => request.delete('/api/v3/notifications/push/subscriptions', { data: { endpoint } })
  },
  reminders: {
    list: spaceId => request.get(`/api/v3/spaces/${spaceId}/reminders`),
    save: (spaceId, type, data) => request.put(`/api/v3/spaces/${spaceId}/reminders/${type}`, data)
  },
  transfer: {
    exportSpace: (spaceId, stepUpToken) => request.get(`/api/v3/spaces/${spaceId}/transfer/export`, { responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }),
    importSpace: (spaceId, formData, stepUpToken) => request.post(`/api/v3/spaces/${spaceId}/transfer/import`, formData, { headers: stepHeader(stepUpToken), timeout: 10 * 60 * 1000 }),
    exportBook: (spaceId, params, stepUpToken) => request.get(`/api/v3/spaces/${spaceId}/books`, { params, responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 })
  },
  shares: {
    create: (spaceId, diaryId, data, stepUpToken) => request.post(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/shares`, data, { headers: stepHeader(stepUpToken) }),
    list: (spaceId, diaryId, stepUpToken) => request.get(`/api/v3/spaces/${spaceId}/diaries/${diaryId}/shares`, { headers: stepHeader(stepUpToken) }),
    open: (token, password) => request.post(`/api/v3/public/shares/${token}/open`, { password }),
    revoke: shareId => request.delete(`/api/v3/shares/${shareId}`)
  },
  ai: {
    reports: async spaceId => { const response = await request.get(`/api/v3/spaces/${spaceId}/ai-reports`); return { ...response, data: { content: response.data || [] } } },
    report: (spaceId, reportId) => request.get(`/api/v3/spaces/${spaceId}/ai-reports/${reportId}`),
    generate: (spaceId, data) => request.post(`/api/v3/spaces/${spaceId}/ai-reports`, data, { timeout: 120000 }),
    schedule: spaceId => request.get(`/api/v3/spaces/${spaceId}/ai/schedule`),
    updateSchedule: (spaceId, data) => request.put(`/api/v3/spaces/${spaceId}/ai/schedule`, data)
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

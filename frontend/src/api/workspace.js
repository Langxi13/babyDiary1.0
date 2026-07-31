import request from '@/utils/request'
import { normalizeDiary, normalizeMedia, normalizeSpace } from '@/api/v3Adapters'

export const workspaceApi = {
  spaces: {
    async list() {
      return (await request.get('/api/v3/spaces') || []).map(normalizeSpace)
    },
    async create(data) {
      return normalizeSpace(await request.post('/api/v3/spaces', {
        ...data,
        defaultVisibility: data.defaultVisibility || 'SHARED'
      }))
    },
    rename: (spaceId, data) => request.put(`/api/v3/spaces/${spaceId}`, data),
    async members(spaceId) {
      return (await request.get(`/api/v3/spaces/${spaceId}/members`) || []).map(member => ({
        ...member,
        avatarMedia: member.avatarMedia ? normalizeMedia(member.avatarMedia) : null
      }))
    },
    invite: (spaceId, data) => request.post(`/api/v3/spaces/${spaceId}/invitations`, data),
    accept: token => request.post(`/api/v3/invitations/${token}/accept`),
    removeMember: (spaceId, accountId) => request.delete(`/api/v3/spaces/${spaceId}/members/${accountId}`),
    updateRole: (spaceId, accountId, role) => request.put(
      `/api/v3/spaces/${spaceId}/members/${accountId}/role`, { role }
    )
  },

  search: (spaceId, query, limit = 30) => request.get(
    `/api/v3/spaces/${spaceId}/search`, { params: { query, limit } }
  ),
  insights: (spaceId, year) => request.get(
    `/api/v3/spaces/${spaceId}/insights/yearly`, { params: { year } }
  ),

  templates: {
    list: spaceId => request.get(`/api/v3/spaces/${spaceId}/templates`),
    create: (spaceId, data) => request.post(`/api/v3/spaces/${spaceId}/templates`, data),
    update: (spaceId, templateId, data) => request.put(
      `/api/v3/spaces/${spaceId}/templates/${templateId}`, data
    ),
    remove: (spaceId, templateId) => request.delete(
      `/api/v3/spaces/${spaceId}/templates/${templateId}`
    )
  },

  sync: {
    pull: (spaceId, cursor = 0, limit = 200) => request.get(
      `/api/v3/spaces/${spaceId}/sync/pull`, { params: { cursor, limit } }
    ),
    push: (spaceId, operations, stepUpToken) => request.post(
      `/api/v3/spaces/${spaceId}/sync/push`,
      { operations },
      { headers: stepHeader(stepUpToken) }
    )
  },

  notifications: {
    list: params => request.get('/api/v3/notifications', { params }),
    async unread() {
      return (await request.get('/api/v3/notifications/unread-count')).count
    },
    read: id => request.put(`/api/v3/notifications/${id}/read`),
    readAll: () => request.put('/api/v3/notifications/read-all'),
    async publicKey() {
      return (await request.get('/api/v3/notifications/push/public-key')).publicKey
    },
    subscribe: data => request.post('/api/v3/notifications/push/subscriptions', data),
    unsubscribe: endpoint => request.delete('/api/v3/notifications/push/subscriptions', {
      data: { endpoint }
    })
  },

  reminders: {
    list: spaceId => request.get(`/api/v3/spaces/${spaceId}/reminders`),
    save: (spaceId, type, data) => request.put(`/api/v3/spaces/${spaceId}/reminders/${type}`, data)
  },

  transfer: {
    exportSpace: (spaceId, stepUpToken) => request.get(
      `/api/v3/spaces/${spaceId}/transfer/export`,
      { responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }
    ),
    importSpace: (spaceId, formData, stepUpToken) => request.post(
      `/api/v3/spaces/${spaceId}/transfer/import`,
      formData,
      { headers: stepHeader(stepUpToken), timeout: 10 * 60 * 1000 }
    ),
    exportBook: (spaceId, params, stepUpToken) => request.get(
      `/api/v3/spaces/${spaceId}/books`,
      { params, responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }
    )
  },

  shares: {
    create: (spaceId, diaryId, data, stepUpToken) => request.post(
      `/api/v3/spaces/${spaceId}/diaries/${diaryId}/shares`,
      data,
      { headers: stepHeader(stepUpToken) }
    ),
    list: (spaceId, diaryId, stepUpToken) => request.get(
      `/api/v3/spaces/${spaceId}/diaries/${diaryId}/shares`,
      { headers: stepHeader(stepUpToken) }
    ),
    async open(token, password) {
      return normalizeDiary(await request.post(`/api/v3/public/shares/${token}/open`, { password }))
    },
    revoke: shareId => request.delete(`/api/v3/shares/${shareId}`)
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

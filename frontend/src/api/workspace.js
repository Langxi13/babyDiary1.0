import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { normalizeDiary, normalizeMedia, normalizeSpace } from '@/api/models'
import { isNativeApp } from '@/platform/runtimeConfig'
import { downloadNativeFile } from '@/platform/nativeFiles'

export const workspaceApi = {
  spaces: {
    async list() {
      return (await request.get(`${API_ROOT}/spaces`) || []).map(normalizeSpace)
    },
    async create(data) {
      return normalizeSpace(await request.post(`${API_ROOT}/spaces`, {
        ...data,
        defaultVisibility: data.defaultVisibility || 'SHARED'
      }))
    },
    rename: (spaceId, data) => request.put(`${API_ROOT}/spaces/${spaceId}`, data),
    async members(spaceId) {
      return (await request.get(`${API_ROOT}/spaces/${spaceId}/members`) || []).map(member => ({
        ...member,
        avatarMedia: member.avatarMedia ? normalizeMedia(member.avatarMedia) : null
      }))
    },
    invite: (spaceId, data) => request.post(`${API_ROOT}/spaces/${spaceId}/invitations`, data),
    accept: token => request.post(`${API_ROOT}/invitations/${token}/accept`),
    removeMember: (spaceId, accountId) => request.delete(`${API_ROOT}/spaces/${spaceId}/members/${accountId}`),
    updateRole: (spaceId, accountId, role) => request.put(
      `${API_ROOT}/spaces/${spaceId}/members/${accountId}/role`, { role }
    )
  },

  search: (spaceId, query, limit = 30) => request.get(
    `${API_ROOT}/spaces/${spaceId}/search`, { params: { query, limit } }
  ),
  insights: (spaceId, year) => request.get(
    `${API_ROOT}/spaces/${spaceId}/insights/yearly`, { params: { year } }
  ),

  templates: {
    list: spaceId => request.get(`${API_ROOT}/spaces/${spaceId}/templates`),
    create: (spaceId, data) => request.post(`${API_ROOT}/spaces/${spaceId}/templates`, data),
    update: (spaceId, templateId, data) => request.put(
      `${API_ROOT}/spaces/${spaceId}/templates/${templateId}`, data
    ),
    remove: (spaceId, templateId) => request.delete(
      `${API_ROOT}/spaces/${spaceId}/templates/${templateId}`
    )
  },

  sync: {
    pull: (spaceId, cursor = 0, limit = 200) => request.get(
      `${API_ROOT}/spaces/${spaceId}/sync/pull`, { params: { cursor, limit } }
    ),
    push: (spaceId, operations, stepUpToken) => request.post(
      `${API_ROOT}/spaces/${spaceId}/sync/push`,
      { operations },
      { headers: stepHeader(stepUpToken) }
    )
  },

  notifications: {
    list: params => request.get(`${API_ROOT}/notifications`, { params }),
    async unread() {
      return (await request.get(`${API_ROOT}/notifications/unread-count`)).count
    },
    read: id => request.put(`${API_ROOT}/notifications/${id}/read`),
    readAll: () => request.put(`${API_ROOT}/notifications/read-all`),
    async publicKey() {
      return (await request.get(`${API_ROOT}/notifications/push/public-key`)).publicKey
    },
    subscribe: data => request.post(`${API_ROOT}/notifications/push/subscriptions`, data),
    unsubscribe: endpoint => request.delete(`${API_ROOT}/notifications/push/subscriptions`, {
      data: { endpoint }
    })
  },

  reminders: {
    list: spaceId => request.get(`${API_ROOT}/spaces/${spaceId}/reminders`),
    save: (spaceId, type, data) => request.put(`${API_ROOT}/spaces/${spaceId}/reminders/${type}`, data)
  },

  transfer: {
    exportSpace: (spaceId, stepUpToken) => isNativeApp()
      ? downloadNativeFile({
          path: `${API_ROOT}/spaces/${spaceId}/transfer/export`,
          headers: stepHeader(stepUpToken),
          filename: 'Baby-Diary-export.zip'
        })
      : request.get(
          `${API_ROOT}/spaces/${spaceId}/transfer/export`,
          { responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }
        ),
    importSpace: (spaceId, formData, stepUpToken) => request.post(
      `${API_ROOT}/spaces/${spaceId}/transfer/import`,
      formData,
      { headers: stepHeader(stepUpToken), timeout: 10 * 60 * 1000 }
    ),
    exportBook: (spaceId, params, stepUpToken) => isNativeApp()
      ? downloadNativeFile({
          path: `${API_ROOT}/spaces/${spaceId}/books`,
          params,
          headers: stepHeader(stepUpToken),
          filename: `Baby-Diary.${params.format || 'pdf'}`
        })
      : request.get(
          `${API_ROOT}/spaces/${spaceId}/books`,
          { params, responseType: 'blob', headers: stepHeader(stepUpToken), timeout: 5 * 60 * 1000 }
        )
  },

  shares: {
    create: (spaceId, diaryId, data, stepUpToken) => request.post(
      `${API_ROOT}/spaces/${spaceId}/diaries/${diaryId}/shares`,
      data,
      { headers: stepHeader(stepUpToken) }
    ),
    list: (spaceId, diaryId, stepUpToken) => request.get(
      `${API_ROOT}/spaces/${spaceId}/diaries/${diaryId}/shares`,
      { headers: stepHeader(stepUpToken) }
    ),
    async open(token, password) {
      return normalizeDiary(await request.post(`${API_ROOT}/public/shares/${token}/open`, { password }))
    },
    revoke: shareId => request.delete(`${API_ROOT}/shares/${shareId}`)
  }
}

function stepHeader(token) {
  return token ? { 'X-Step-Up-Token': token } : {}
}

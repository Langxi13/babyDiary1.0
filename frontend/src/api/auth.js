import request from '@/utils/request'
import { nativeAuthResultRequest } from '@/platform/nativeAuth'
import { isNativeApp } from '@/platform/runtimeConfig'
import { resolveServerUrl } from '@/platform/runtimeConfig'

const normalizeUser = user => user ? {
  ...user,
  userId: user.id || user.accountId,
  systemRole: user.systemRole || user.role,
  avatarMedia: user.avatarMedia ? {
    ...user.avatarMedia,
    contentUrl: resolveServerUrl(user.avatarMedia.contentUrl)
  } : null
} : user

const normalizeSession = response => ({
  ...response,
  data: { ...response.data, userInfo: normalizeUser(response.data?.userInfo || response.data) }
})

export const authApi = {
  login(data) {
    if (isNativeApp()) return nativeAuthResultRequest('POST', '/api/v3/auth/login', data).then(normalizeSession)
    return request.post('/api/v3/auth/login', data).then(normalizeSession)
  },

  register(data) {
    return request.post('/api/v3/auth/register', data)
  },

  logout(accessToken) {
    if (isNativeApp()) {
      return nativeAuthResultRequest('POST', '/api/v3/auth/logout', null,
        accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
    }
    return request.post('/api/v3/auth/logout', null, {
      __skipAuthRecovery: true,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      timeout: 10000
    })
  },

  getUserInfo() {
    return request.get('/api/v3/account/profile').then(response => ({ ...response, data: normalizeUser(response.data) }))
  },

  async uploadAvatar(formData) {
    const { activeSpaceId, normalizeMedia } = await import('@/api/v3Adapters')
    const spaceId = await activeSpaceId()
    const file = formData.get('avatarFile')
    const mediaBody = new FormData()
    mediaBody.append('file', file)
    const upload = await request.post(`/api/v3/spaces/${spaceId}/media`, mediaBody, { timeout: 10 * 60 * 1000 })
    const media = normalizeMedia(upload.data)
    try {
      const response = await request.put('/api/v3/account/avatar', { spaceId, assetId: media.assetId })
      return { ...response, data: normalizeUser(response.data) }
    } catch (error) {
      await request.delete(`/api/v3/spaces/${spaceId}/media/${media.assetId}`, { __silentError: true }).catch(() => {})
      throw error
    }
  },

  changePassword(data) {
    return request.post('/api/v3/account/password', {
      currentPassword: data.currentPassword || data.oldPassword,
      newPassword: data.newPassword
    })
  },

  getSessions() {
    return request.get('/api/v3/auth/sessions')
  },

  revokeSession(sessionId) {
    return request.delete(`/api/v3/auth/sessions/${sessionId}`)
  },

  updateEmail(data) {
    return request.put('/api/v3/account/email', { email: data.email })
      .then(response => ({
        ...response,
        message: response.data.mailSent
          ? '邮箱已更新，验证邮件已发送'
          : '邮箱已更新；当前未启用邮件服务，请联系管理员完成验证',
        data: normalizeUser(response.data.profile),
        mailSent: response.data.mailSent
      }))
  },

  confirmEmail(token) {
    return request.post('/api/v3/auth/email/confirm', { token })
  },

  stepUp(password) {
    return request.post('/api/v3/auth/step-up', { password }, {
      __silentError: true,
      __skipAuthRecovery: true
    })
  },

  recoveryCodes(password) {
    return request.post('/api/v3/auth/recovery-codes', { password })
  },

  requestPasswordReset(email) {
    return request.post('/api/v3/auth/password/reset-request', { email })
  },

  resetPassword(token, newPassword) {
    return request.post('/api/v3/auth/password/reset', { token, newPassword })
  },

  recoverPassword(username, recoveryCode, newPassword) {
    return request.post('/api/v3/auth/password/recover', { username, recoveryCode, newPassword })
  }
}

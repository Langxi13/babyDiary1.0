import request from '@/utils/request'
import { nativeAuthResultRequest } from '@/platform/nativeAuth'
import { isNativeApp } from '@/platform/runtimeConfig'
import { normalizeMedia } from '@/api/v3Adapters'

const normalizeUser = user => user ? {
  ...user,
  avatarMedia: user.avatarMedia ? normalizeMedia(user.avatarMedia) : null
} : user

const normalizeSession = session => ({
  ...session,
  userInfo: normalizeUser(session?.userInfo)
})

export const authApi = {
  async login(data) {
    const session = isNativeApp()
      ? await nativeAuthResultRequest('POST', '/api/v3/auth/login', data)
      : await request.post('/api/v3/auth/login', data)
    return normalizeSession(session)
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

  async getUserInfo() {
    return normalizeUser(await request.get('/api/v3/account/profile'))
  },

  async uploadAvatar(spaceId, file) {
    const mediaBody = new FormData()
    mediaBody.append('file', file)
    const media = normalizeMedia(await request.post(`/api/v3/spaces/${spaceId}/media`, mediaBody, {
      timeout: 10 * 60 * 1000
    }))
    try {
      return normalizeUser(await request.put('/api/v3/account/avatar', { spaceId, assetId: media.id }))
    } catch (error) {
      await request.delete(`/api/v3/spaces/${spaceId}/media/${media.id}`, { __silentError: true }).catch(() => {})
      throw error
    }
  },

  changePassword(data) {
    return request.post('/api/v3/account/password', {
      currentPassword: data.currentPassword,
      newPassword: data.newPassword
    })
  },

  getSessions() {
    return request.get('/api/v3/auth/sessions')
  },

  revokeSession(sessionId) {
    return request.delete(`/api/v3/auth/sessions/${sessionId}`)
  },

  async updateEmail(data) {
    const result = await request.put('/api/v3/account/email', { email: data.email })
    return {
      ...result,
      profile: normalizeUser(result.profile)
    }
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

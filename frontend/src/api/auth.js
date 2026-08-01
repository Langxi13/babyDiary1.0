import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'
import { nativeAuthResultRequest } from '@/platform/nativeAuth'
import { isNativeApp } from '@/platform/runtimeConfig'
import { normalizeMedia } from '@/api/models'
import { mediaApi } from '@/api/media'

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
      ? await nativeAuthResultRequest('POST', `${API_ROOT}/auth/login`, data)
      : await request.post(`${API_ROOT}/auth/login`, data)
    return normalizeSession(session)
  },

  register(data) {
    return request.post(`${API_ROOT}/auth/register`, data)
  },

  logout(accessToken) {
    if (isNativeApp()) {
      return nativeAuthResultRequest('POST', `${API_ROOT}/auth/logout`, null,
        accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
    }
    return request.post(`${API_ROOT}/auth/logout`, null, {
      __skipAuthRecovery: true,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      timeout: 10000
    })
  },

  async getUserInfo() {
    return normalizeUser(await request.get(`${API_ROOT}/account/profile`))
  },

  async uploadAvatar(spaceId, file) {
    const media = normalizeMedia(await mediaApi.uploadSource(spaceId, file))
    try {
      return normalizeUser(await request.put(`${API_ROOT}/account/avatar`, { spaceId, assetId: media.id }))
    } catch (error) {
      await request.delete(`${API_ROOT}/spaces/${spaceId}/media/${media.id}`, { __silentError: true }).catch(() => {})
      throw error
    }
  },

  changePassword(data) {
    return request.post(`${API_ROOT}/account/password`, {
      currentPassword: data.currentPassword,
      newPassword: data.newPassword
    })
  },

  getSessions(accessToken) {
    if (isNativeApp()) {
      return nativeAuthResultRequest('GET', `${API_ROOT}/auth/sessions`, null,
        accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
    }
    return request.get(`${API_ROOT}/auth/sessions`)
  },

  revokeSession(sessionId) {
    return request.delete(`${API_ROOT}/auth/sessions/${sessionId}`)
  },

  async updateEmail(data) {
    const result = await request.put(`${API_ROOT}/account/email`, { email: data.email })
    return {
      ...result,
      profile: normalizeUser(result.profile)
    }
  },

  confirmEmail(token) {
    return request.post(`${API_ROOT}/auth/email/confirm`, { token })
  },

  stepUp(password) {
    return request.post(`${API_ROOT}/auth/step-up`, { password }, {
      __silentError: true,
      __skipAuthRecovery: true
    })
  },

  recoveryCodes(password) {
    return request.post(`${API_ROOT}/auth/recovery-codes`, { password })
  },

  requestPasswordReset(email) {
    return request.post(`${API_ROOT}/auth/password/reset-request`, { email })
  },

  resetPassword(token, newPassword) {
    return request.post(`${API_ROOT}/auth/password/reset`, { token, newPassword })
  },

  recoverPassword(username, recoveryCode, newPassword) {
    return request.post(`${API_ROOT}/auth/password/recover`, { username, recoveryCode, newPassword })
  }
}

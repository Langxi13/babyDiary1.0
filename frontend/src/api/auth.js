import request from '@/utils/request'
import { nativeAuthResultRequest } from '@/platform/nativeAuth'
import { isNativeApp } from '@/platform/runtimeConfig'

export const authApi = {
  login(data) {
    if (isNativeApp()) return nativeAuthResultRequest('POST', '/api/v2/auth/login', data)
    return request.post('/api/v2/auth/login', data)
  },

  register(data) {
    return request.post('/api/auth/register', data)
  },

  logout(accessToken) {
    if (isNativeApp()) {
      return nativeAuthResultRequest('POST', '/api/v2/auth/logout', null,
        accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
    }
    return request.post('/api/v2/auth/logout', null, {
      __skipAuthRecovery: true,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      timeout: 10000
    })
  },

  getUserInfo() {
    return request.get('/api/auth/info')
  },

  uploadAvatar(formData) {
    return request.post('/api/auth/avatar', formData)
  },

  changePassword(data) {
    return request.put('/api/auth/password', data)
  },

  getSessions() {
    return request.get('/api/v2/auth/sessions')
  },

  revokeSession(sessionId) {
    return request.delete(`/api/v2/auth/sessions/${sessionId}`)
  },

  updateEmail(data) {
    return request.put('/api/v2/auth/email', data)
  },

  confirmEmail(token) {
    return request.post('/api/v2/auth/email/confirm', { token })
  },

  stepUp(password) {
    return request.post('/api/v2/auth/step-up', { password }, { __silentError: true })
  },

  recoveryCodes(password) {
    return request.post('/api/v2/auth/recovery-codes', { password })
  },

  requestPasswordReset(email) {
    return request.post('/api/v2/auth/password/reset-request', { email })
  },

  resetPassword(token, newPassword) {
    return request.post('/api/v2/auth/password/reset', { token, newPassword })
  },

  recoverPassword(username, recoveryCode, newPassword) {
    return request.post('/api/v2/auth/password/recover', { username, recoveryCode, newPassword })
  }
}

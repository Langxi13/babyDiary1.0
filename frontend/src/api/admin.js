import request from '@/utils/request'

const stepUpHeaders = token => token ? { 'X-Step-Up-Token': token } : {}

export const adminApi = {
  getInvitationCode(stepUpToken) {
    return request.get('/api/admin/invitation-code', {
      headers: stepUpHeaders(stepUpToken)
    })
  },

  rotateInvitationCode(stepUpToken) {
    return request.post('/api/admin/invitation-code/rotate', null, {
      headers: stepUpHeaders(stepUpToken)
    })
  }
}

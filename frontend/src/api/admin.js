import request from '@/utils/request'

const stepUpHeaders = token => token ? { 'X-Step-Up-Token': token } : {}

export const adminApi = {
  getInvitationCode(stepUpToken) {
    return request.post('/api/v3/admin/invitation-code/view', null, {
      headers: stepUpHeaders(stepUpToken)
    })
  },

  rotateInvitationCode(stepUpToken) {
    return request.post('/api/v3/admin/invitation-code/rotate', null, {
      headers: stepUpHeaders(stepUpToken)
    })
  }
}

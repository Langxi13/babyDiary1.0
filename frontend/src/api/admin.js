import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'

const stepUpHeaders = token => token ? { 'X-Step-Up-Token': token } : {}

export const adminApi = {
  getInvitationCode(stepUpToken) {
    return request.post(`${API_ROOT}/admin/invitation-code/view`, null, {
      headers: stepUpHeaders(stepUpToken)
    })
  },

  rotateInvitationCode(stepUpToken) {
    return request.post(`${API_ROOT}/admin/invitation-code/rotate`, null, {
      headers: stepUpHeaders(stepUpToken)
    })
  }
}

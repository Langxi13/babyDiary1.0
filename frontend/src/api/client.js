import { API_ROOT } from '@/api/contract'
import request from '@/utils/request'

export const clientApi = {
  bootstrap() {
    return request.get(`${API_ROOT}/client/bootstrap`, {
      __silentError: true,
      timeout: 10000
    })
  }
}

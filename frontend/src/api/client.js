import request from '@/utils/request'

export const clientApi = {
  bootstrap() {
    return request.get('/api/v2/client/bootstrap', {
      __silentError: true,
      timeout: 10000
    })
  }
}

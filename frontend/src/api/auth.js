import request from '@/utils/request'

export const authApi = {
  login(data) {
    return request.post('/api/auth/login', data)
  },

  register(data) {
    return request.post('/api/auth/register', data)
  },

  logout() {
    return request.post('/api/auth/logout')
  },

  getUserInfo() {
    return request.get('/api/auth/info')
  },

  uploadAvatar(formData) {
    return request.post('/api/auth/avatar', formData)
  },

  changePassword(data) {
    return request.put('/api/auth/password', data)
  }
}

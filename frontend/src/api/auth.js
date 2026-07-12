import request from './request'

export const authApi = {
  casLogin(token, cookies) {
    return request.post('/auth/cas/login', { token, cookies })
  },
  casCredentialLogin(username, password) {
    return request.post('/auth/cas/credential-login', { username, password })
  },
  login(data) {
    return request.post('/auth/local/login', data)
  },
  getUserInfo() {
    return request.get('/auth/info')
  },
  logout() {
    return request.post('/auth/logout')
  },
  changePassword(oldPassword, newPassword) {
    return request.put('/auth/change-password', { oldPassword, newPassword })
  }
}

import request from './index'

export function register(username, password) {
  return request.post('/user/register', { username, password })
}

export function login(username, password) {
  return request.post('/user/login', { username, password })
}

export function logout() {
  return request.post('/user/logout')
}

export function refreshToken(refreshToken) {
  return request.post('/user/refresh', { refreshToken })
}

export function getUserInfo() {
  return request.get('/user/info')
}

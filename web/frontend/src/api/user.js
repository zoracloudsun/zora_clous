import request from './index'

export function getCaptcha() {
  return request.get('/user/captcha')
}

export function sendCode(email) {
  return request.post('/user/send-code', { email })
}

export function register(email, password, code) {
  return request.post('/user/register', { email, password, code })
}

export function login(email, password, captchaId, captchaCode) {
  return request.post('/user/login', { email, password, captchaId, captchaCode })
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

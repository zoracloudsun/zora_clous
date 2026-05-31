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

// ==================== 微信扫码登录 ====================

/** 生成微信扫码场景，返回 { sceneId, authUrl } */
export function getWechatQrcode() {
  return request.post('/user/wechat/qrcode')
}

/** 轮询扫码状态：pending | scanned | confirmed | expired */
export function checkWechatScan(sceneId) {
  return request.get('/user/wechat/check', { params: { sceneId } })
}

/** 微信绑定邮箱专用：发送验证码（允许已注册邮箱） */
export function sendBindCode(email) {
  return request.post('/user/send-bind-code', { email })
}

/** 微信扫码后绑定邮箱 */
export function bindWechatEmail(sceneId, email, code) {
  return request.post('/user/wechat/bind-email', { sceneId, email, code })
}

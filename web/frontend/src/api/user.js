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

// ==================== 邮箱找回密码 ====================

/** 发送密码重置验证码 */
export function sendResetCode(email) {
  return request.post('/user/forgot-password/send-code', { email })
}

/** 验证码校验 + 重置密码 */
export function resetPassword(email, password, code) {
  return request.post('/user/forgot-password/reset', { email, password, code })
}

// ==================== RBAC 角色权限 ====================

/** Admin: 分页查询所有用户 */
export function getAdminUsers(page = 1, size = 10) {
  return request.get('/user/admin/users', { params: { page, size } })
}

/** 获取当前登录用户信息 */
export function getCurrentUser() {
  return request.get('/user/me')
}

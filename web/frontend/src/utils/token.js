// 双 Token 存储：accessToken 和 refreshToken 分别用独立 key 存储
// - zyt_access_token：短期（30min），每次请求都携带，高频读写
// - zyt_refresh_token：长期（7天），仅在自动刷新时使用，低频读取
// - zyt_user_role：用户角色，登录时存储，路由守卫和 UI 渲染时读取
// 分开存储避免 JSON 序列化开销，各自生命周期独立管理

const ACCESS_TOKEN_KEY = 'zyt_access_token'
const REFRESH_TOKEN_KEY = 'zyt_refresh_token'
const ROLE_KEY = 'zyt_user_role'

// 获取 accessToken（每次 API 请求时调用）
export function getToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

// 获取 refreshToken（仅在 401 自动刷新时调用）
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

// 获取用户角色
export function getRole() {
  return localStorage.getItem(ROLE_KEY)
}

// 存储用户角色
export function setRole(role) {
  if (role) {
    localStorage.setItem(ROLE_KEY, role)
  }
}

// 登录成功后同时存储两个 Token + 角色
export function setTokens(accessToken, refreshToken, role) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  if (role) {
    localStorage.setItem(ROLE_KEY, role)
  }
}

// 刷新后只更新 accessToken + role，refreshToken 不变
export function setToken(token) {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

// 登出时清除所有 Token + 角色
export function removeToken() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
}

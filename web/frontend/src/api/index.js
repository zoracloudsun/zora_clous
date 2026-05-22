import axios from 'axios'
import { getToken, getRefreshToken, setToken, removeToken } from '@/utils/token'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/',
  timeout: 10000,
})

// ==================== 双 Token 自动无感刷新机制 ====================
// 当 accessToken 过期（后端返回 401）时，自动用 refreshToken 换取新的
// accessToken，然后重试原请求。用户完全无感知，只有在 refreshToken 也
// 过期时才会跳转登录页。

// 刷新锁：确保同一时间只有一个刷新请求，防止并发刷新
let isRefreshing = false
// 请求队列：刷新期间到达的其他 401 请求在此排队，刷新完成后用新 Token 重试
let pendingRequests = []

// 用新 Token 重放所有排队的请求
function onRefreshed(newToken) {
  pendingRequests.forEach((callback) => callback(newToken))
  pendingRequests = []
}

// 将请求加入排队队列
function addPendingRequest(callback) {
  pendingRequests.push(callback)
}

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg))
    }
    return res
  },
  async (error) => {
    const { config, response } = error
    // accessToken 过期或无效 → 尝试自动刷新
    // config._retry 标记防止刷新后仍然 401 导致死循环
    if (response && response.status === 401 && !config._retry) {
      const refreshToken = getRefreshToken()
      // 没有 refreshToken，无法刷新，直接踢到登录页
      if (!refreshToken) {
        removeToken()
        ElMessage.error('登录已失效，请重新登录')
        window.location.href = '/login'
        return Promise.reject(error)
      }
      // 已有刷新请求在进行中，当前请求加入等待队列
      // 避免多个请求同时发现 401 时各自独立刷新，造成竞态条件
      if (isRefreshing) {
        return new Promise((resolve) => {
          addPendingRequest((newToken) => {
            config.headers.Authorization = newToken
            config._retry = true
            resolve(request(config))
          })
        })
      }
      // 第一个发现 401 的请求：执行刷新
      isRefreshing = true
      config._retry = true
      try {
        // 用 refreshToken 换取新的 accessToken
        const res = await axios.post('/user/refresh', { refreshToken })
        const newAccessToken = res.data.data.accessToken
        // 更新 localStorage，通知所有排队请求，重试当前请求
        setToken(newAccessToken)
        onRefreshed(newAccessToken)
        config.headers.Authorization = newAccessToken
        return request(config)
      } catch (refreshError) {
        // refreshToken 也过期或失效了，彻底登出
        removeToken()
        ElMessage.error('登录已失效，请重新登录')
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  },
)

export default request

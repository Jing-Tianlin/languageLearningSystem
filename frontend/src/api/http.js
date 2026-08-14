import axios from 'axios'
import { API_BASE_URL } from '@/config'

/**
 * 共享 axios 实例：所有请求（fetchJson 与 client）的唯一出口。
 * - 任何 HTTP 状态码都 resolve，由上层决定如何解读 {code, message, data}
 * - 自动注入 Authorization（JWT）与 X-Target-Language（当前学习语言）头
 * - 401 时清理登录态并跳转 /login（/user/login 自身除外）
 */
export const rawClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000,
  validateStatus: () => true,
})

rawClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const langCode = localStorage.getItem('lastLangCode')
  if (langCode) {
    config.headers['X-Target-Language'] = langCode
  }
  return config
})

export function handleUnauthorized() {
  import('@/stores/auth')
    .then(({ useAuthStore }) => useAuthStore().logout())
    .finally(() => {
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    })
}

rawClient.interceptors.response.use(
  (response) => {
    const url = response.config?.url || ''
    const isLoginRequest = url.includes('/user/login')
    if (!isLoginRequest && (response.status === 401 || response.data?.code === 401)) {
      handleUnauthorized()
      return Promise.reject(new Error('未登录或登录已过期'))
    }
    return response.data
  },
  (error) => {
    // 仅网络层错误（断网/超时）会走到这里；HTTP 错误已在上面处理
    return Promise.reject(error)
  },
)

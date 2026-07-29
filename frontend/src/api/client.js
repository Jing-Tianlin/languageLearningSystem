import axios from 'axios'
import { API_BASE_URL } from '@/config'

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 自动注入当前学习语言
  const langCode = localStorage.getItem('lastLangCode')
  if (langCode) {
    config.headers['X-Target-Language'] = langCode
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data || true
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default client

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import { API_BASE_URL } from '@/config'
import { initStudyReminder } from '@/composables/useStudyReminder'

import './assets/main.css'

// 全局包装 fetch：为 API 请求自动附加 Authorization，保证鉴权接口统一携带 token
const originalFetch = window.fetch
window.fetch = (input, init = {}) => {
  const url = typeof input === 'string' ? input : input.url
  const isApiRequest = url.startsWith(API_BASE_URL) || url.startsWith('/') || url.startsWith('./') || url.startsWith('../')
  if (isApiRequest) {
    const token = localStorage.getItem('token')
    if (token) {
      const headers = new Headers(init.headers || {})
      if (!headers.has('Authorization')) {
        headers.set('Authorization', 'Bearer ' + token)
      }
      init = { ...init, headers }
    }
  }
  return originalFetch(input, init)
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

// 每日学习提醒 + PWA
initStudyReminder()

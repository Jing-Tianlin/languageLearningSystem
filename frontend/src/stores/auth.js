import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '@/api/user'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || null)

  const isLoggedIn = computed(() => !!token.value)

  // 目标学习语言 (响应式全局状态)
  const targetLanguage = ref(localStorage.getItem('lastLangCode') || null)
  // 目标考试等级 (响应式全局状态, -1=全部等级)
  const targetLevel = ref(loadStoredLevel())

  function loadStoredLevel() {
    const raw = localStorage.getItem('lastLevel')
    if (raw === null || raw === '') return null
    const parsed = parseInt(raw, 10)
    return isNaN(parsed) ? null : parsed
  }

  // 登录/注册成功后统一写入会话状态（user、token、语言等级偏好）
  function applyUserSession(u, tokenStr) {
    user.value = u
    token.value = tokenStr
    localStorage.setItem('token', tokenStr)
    localStorage.setItem('userId', u.id)
    if (u.currentLangCode) {
      targetLanguage.value = u.currentLangCode
      localStorage.setItem('lastLangCode', u.currentLangCode)
    }
    if (u.currentLevel !== null && u.currentLevel !== undefined && u.currentLevel !== '') {
      const lv = parseInt(u.currentLevel, 10)
      if (!isNaN(lv)) {
        targetLevel.value = lv
        localStorage.setItem('lastLevel', u.currentLevel)
      }
    }
  }

  function setTargetLanguage(code) {
    targetLanguage.value = code
    localStorage.setItem('lastLangCode', code)
    if (isLoggedIn.value && user.value?.id) {
      fetchJson(API_BASE_URL + '/user/preferences?userId=' + user.value.id + '&langCode=' + encodeURIComponent(code), {
        method: 'PUT'
      }).catch(() => {})
    }
  }

  function setTargetLevel(level) {
    targetLevel.value = level
    localStorage.setItem('lastLevel', level !== null ? String(level) : '')
    if (isLoggedIn.value && user.value?.id) {
      const levelStr = level !== null ? String(level) : ''
      fetchJson(API_BASE_URL + '/user/preferences?userId=' + user.value.id + '&level=' + encodeURIComponent(levelStr), {
        method: 'PUT'
      }).catch(() => {})
    }
  }

  async function login(username, password) {
    const json = await fetchJson(API_BASE_URL + '/user/login', {
      method: 'POST',
      body: { username, password }
    })
    if (json.code === 200 && json.data) {
      applyUserSession(json.data.user, json.data.token)
      return json.data.user
    }
    throw new Error(json.message || '用户名或密码错误')
  }

  async function register(form) {
    const payload = {
      username: form.username,
      password: form.password,
      nickname: form.nickname || form.username,
    }
    if (form.email && form.email.trim()) payload.email = form.email.trim()
    if (form.phone && form.phone.trim()) payload.phone = form.phone.trim()
    const json = await fetchJson(API_BASE_URL + '/user/register', {
      method: 'POST',
      body: payload
    })
    if (json.code === 200 && json.data) {
      applyUserSession(json.data.user, json.data.token)
      return json.data.user
    }
    throw new Error(json.message || '注册失败')
  }

  function logout() {
    user.value = null
    token.value = null
    targetLanguage.value = null
    targetLevel.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    // 清理语言/等级偏好，避免换账号后沿用上一账号的设置
    localStorage.removeItem('lastLangCode')
    localStorage.removeItem('lastLevel')
    localStorage.removeItem('flashcards-settings')
  }

  async function fetchProfile() {
    const userId = localStorage.getItem('userId')
    if (userId) {
      try {
        user.value = await userApi.getUserById(userId)
        try {
          const j = await fetchJson(API_BASE_URL + '/admin/user-roles/' + userId)
          if (j.code === 200 && j.data) user.value.roles = j.data.map(x => x.code || x.name)
        } catch (e) { /* 非关键 */ }
      } catch (e) {
        logout()
      }
    }
  }

  return { user, token, isLoggedIn, targetLanguage, targetLevel, setTargetLanguage, setTargetLevel, login, register, logout, fetchProfile }
})

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '@/api/user'
import { API_BASE_URL } from '@/config'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || null)

  const isLoggedIn = computed(() => !!token.value)

  // 目标学习语言 (响应式全局状态)
  const targetLanguage = ref(localStorage.getItem('lastLangCode') || null)
  // 目标考试等级 (响应式全局状态, -1=全部等级)
  const targetLevel = ref(localStorage.getItem('lastLevel') !== null && localStorage.getItem('lastLevel') !== '' ? parseInt(localStorage.getItem('lastLevel')) : null)

  function setTargetLanguage(code) {
    targetLanguage.value = code
    localStorage.setItem('lastLangCode', code)
    if (isLoggedIn.value && user.value?.id) {
      fetch(API_BASE_URL + '/user/preferences?userId=' + user.value.id + '&langCode=' + encodeURIComponent(code), {
        method: 'PUT'
      }).catch(() => {})
    }
  }

  function setTargetLevel(level) {
    targetLevel.value = level
    localStorage.setItem('lastLevel', level !== null ? String(level) : '')
    if (isLoggedIn.value && user.value?.id) {
      const levelStr = level !== null ? String(level) : ''
      fetch(API_BASE_URL + '/user/preferences?userId=' + user.value.id + '&level=' + encodeURIComponent(levelStr), {
        method: 'PUT'
      }).catch(() => {})
    }
  }

  async function login(username, password) {
    const res = await fetch(API_BASE_URL + '/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      const u = json.data.user
      user.value = u
      token.value = json.data.token
      localStorage.setItem('token', token.value)
      localStorage.setItem('userId', u.id)
      if (u.currentLangCode) {
        targetLanguage.value = u.currentLangCode
        localStorage.setItem('lastLangCode', u.currentLangCode)
      }
      if (u.currentLevel !== null && u.currentLevel !== undefined && u.currentLevel !== '') {
        const lv = parseInt(u.currentLevel)
        if (!isNaN(lv)) {
          targetLevel.value = lv
          localStorage.setItem('lastLevel', u.currentLevel)
        }
      }
      return u
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
    const res = await fetch(API_BASE_URL + '/user/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      const u = json.data.user
      user.value = u
      token.value = json.data.token
      localStorage.setItem('token', token.value)
      localStorage.setItem('userId', u.id)
      if (u.currentLangCode) {
        targetLanguage.value = u.currentLangCode
        localStorage.setItem('lastLangCode', u.currentLangCode)
      }
      if (u.currentLevel !== null && u.currentLevel !== undefined && u.currentLevel !== '') {
        const lv = parseInt(u.currentLevel)
        if (!isNaN(lv)) {
          targetLevel.value = lv
          localStorage.setItem('lastLevel', u.currentLevel)
        }
      }
      return u
    }
    throw new Error(json.message || '注册失败')
  }

  async function logLogin(uid, name, success) { try { await fetch(API_BASE_URL+'/admin/logs', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({userId:uid,module:'auth',action:success?'login_success':'login_fail',detail:name}) }) } catch(e) {} }
function setDefaultRole(uid) { fetch(API_BASE_URL+'/admin/user-roles', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({userId:uid,roleId:1}) }).catch(()=>{}) }

function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
  }

  async function fetchProfile() {
    const userId = localStorage.getItem('userId')
    if (userId) {
      try {
        user.value = await userApi.getUserById(userId)
        try {
          const headers = { 'Content-Type': 'application/json' }
          if (token.value) headers['Authorization'] = 'Bearer ' + token.value
          const r = await fetch(API_BASE_URL + '/admin/user-roles/' + userId, { headers })
          const j = await r.json()
          if (j.code === 200 && j.data) user.value.roles = j.data.map(x => x.code || x.name)
        } catch (e) { /* 非关键 */ }
      } catch (e) {
        logout()
      }
    }
  }

  return { user, token, isLoggedIn, targetLanguage, targetLevel, setTargetLanguage, setTargetLevel, login, register, logout, fetchProfile }
})

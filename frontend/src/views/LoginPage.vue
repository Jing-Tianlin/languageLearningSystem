<script setup>
/**
 * LoginPage.vue — 登录 / 注册一体页
 * 默认显示登录表单，点击"注册新账号"切换到注册表单
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getExamLevels, getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'

const router = useRouter()
const authStore = useAuthStore()

const isRegister = ref(false)
const pageTitle = ref('Amazing Teaching')

// 注册表单
const regUsername = ref('')
const regPassword = ref('')
const regPassword2 = ref('')
const regNickname = ref('')
const regEmail = ref('')
const step = ref(1) // 1=填写信息 2=选择语言等级

// 语言和等级选择
const selectedLang = ref('en')
const selectedLevel = ref(null)
const examLevels = computed(() => getExamLevels(selectedLang.value))
const langList = ref([
  { code: 'en', name: '英语', flag: '🇬🇧' },
  { code: 'ja', name: '日语', flag: '🇯🇵' },
  { code: 'ko', name: '韩语', flag: '🇰🇷' },
  { code: 'fr', name: '法语', flag: '🇫🇷' },
  { code: 'de', name: '德语', flag: '🇩🇪' },
])

// 登录表单
const loginUsername = ref('')
const loginPassword = ref('')

const errorMsg = ref('')
const successMsg = ref('')
const loading = ref(false)

async function handleLogin() {
  errorMsg.value = ''
  if (!loginUsername.value || !loginPassword.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await authStore.login(loginUsername.value, loginPassword.value)
    router.push('/')
  } catch (e) {
    errorMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function handleRegNext() {
  if (!regUsername.value || !regPassword.value) {
    errorMsg.value = '请填写用户名和密码'
    return
  }
  if (regPassword.value !== regPassword2.value) {
    errorMsg.value = '两次密码输入不一致'
    return
  }
  if (regPassword.value.length < 4) {
    errorMsg.value = '密码至少需要4位'
    return
  }
  errorMsg.value = ''
  step.value = 2
}

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  // 第2步检查：必须选择语言等级
  if (selectedLevel.value === null) {
    errorMsg.value = '请选择你的学习等级'
    return
  }
  loading.value = true
  try {
    const newUser = await authStore.register({
      username: regUsername.value,
      password: regPassword.value,
      nickname: regNickname.value || regUsername.value,
      email: regEmail.value.trim() || undefined,
    })
    // 注册成功后设置语言和等级
    authStore.setTargetLanguage(selectedLang.value)
    authStore.setTargetLevel(selectedLevel.value !== null ? selectedLevel.value : -1)
    // 同步到用户表（失败也不影响注册）
    try {
      await fetch(`${API_BASE_URL}/user/users`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: newUser.id, currentLangCode: selectedLang.value }),
      })
    } catch (e) { /* 非关键 */ }
    successMsg.value = '注册成功！正在跳转...'
    setTimeout(() => router.push('/'), 800)
  } catch (e) {
    errorMsg.value = e.message || '注册失败'
  } finally { loading.value = false }
}

function toggleMode() {
  isRegister.value = !isRegister.value
  step.value = 1
  errorMsg.value = ''
  successMsg.value = ''
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <LetterSwapTitle :text="pageTitle" tag="h1" color="#5a7d96" />
        <p class="login-subtitle">{{ isRegister ? '创建你的学习账号' : '多语言学习平台' }}</p>
      </div>

      <!-- === 登录表单 === -->
      <form v-if="!isRegister" class="login-form" @submit.prevent="handleLogin">
        <div class="input-group">
          <label>用户名</label>
          <input v-model="loginUsername" type="text" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="input-group">
          <label>密码</label>
          <input v-model="loginPassword" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
        <p class="switch-hint">
          还没有账号？<a href="#" @click.prevent="toggleMode">注册新账号</a>
        </p>
      </form>

      <!-- === 注册表单 === -->
      <form v-else class="login-form" @submit.prevent="step === 1 ? handleRegNext() : handleRegister()">
        <template v-if="step === 1">
          <div class="input-group">
            <label>用户名 *</label>
            <input v-model="regUsername" type="text" placeholder="请输入用户名" autocomplete="username" />
          </div>
          <div class="input-group">
            <label>昵称</label>
            <input v-model="regNickname" type="text" placeholder="给自己取个名字吧" />
          </div>
          <div class="input-group">
            <label>邮箱</label>
            <input v-model="regEmail" type="email" placeholder="选填，用于找回密码" autocomplete="email" />
          </div>
          <div class="input-group">
            <label>密码 *</label>
            <input v-model="regPassword" type="password" placeholder="至少4位密码" autocomplete="new-password" />
          </div>
          <div class="input-group">
            <label>确认密码 *</label>
            <input v-model="regPassword2" type="password" placeholder="再次输入密码" autocomplete="new-password" />
          </div>
        </template>

        <template v-if="step === 2">
          <p class="reg-hint">选择你要学习的语言和等级</p>
          <div class="lang-grid">
            <button v-for="l in langList" :key="l.code"
              class="lang-opt" :class="{ active: selectedLang === l.code }"
              @click.prevent="selectedLang = l.code">
              <span class="lang-flag">{{ l.flag }}</span>
              <span class="lang-name">{{ l.name }}</span>
            </button>
          </div>
          <div class="level-grid">
            <button class="level-opt" :class="{ active: selectedLevel === -1 }" @click.prevent="selectedLevel = -1">全部等级</button>
            <button v-for="lv in examLevels" :key="lv.value"
              class="level-opt" :class="{ active: selectedLevel === lv.value }"
              @click.prevent="selectedLevel = lv.value">
              {{ lv.examLabel }} · {{ lv.examName }}
            </button>
          </div>
        </template>

        <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="login-success">{{ successMsg }}</p>
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '注册中...' : step === 1 ? '下一步 →' : '完成注册' }}
        </button>
        <p class="switch-hint">
          已有账号？<a href="#" @click.prevent="toggleMode">返回登录</a>
        </p>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  width: 100%;
  max-width: 420px;
  margin: 0 auto;
}
.login-card {
  width: 100%;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 24px;
  padding: 40px 38px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header :deep(.letter-swap-title) {
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #888;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.input-group label {
  font-size: 13px;
  color: #555;
  font-weight: 600;
}

.input-group input {
  padding: 12px 16px;
  border-radius: 12px;
  border: 1.5px solid #e5e5e5;
  background: #fafafa;
  color: #1a1028;
  font-size: 15px;
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s;
}

.input-group input::placeholder {
  color: #c0c0c0;
}

.input-group input:focus {
  border-color: #7c9db5;
  box-shadow: 0 0 0 3px rgba(124, 157, 181, 0.12);
}

.login-error {
  color: #e74c3c;
  font-size: 13px;
  text-align: center;
  background: #fef0ef;
  padding: 8px 14px;
  border-radius: 8px;
}

.login-success {
  color: #27ae60;
  font-size: 13px;
  text-align: center;
  background: #eefaf3;
  padding: 8px 14px;
  border-radius: 8px;
}

.login-btn {
  padding: 14px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.3s, transform 0.25s ease, box-shadow 0.25s;
  font-family: var(--font-heading);
  letter-spacing: 0.5px;
  box-shadow: 0 4px 16px rgba(90, 125, 150, 0.25);
}

.login-btn:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(90, 125, 150, 0.35);
}

.login-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.switch-hint {
  text-align: center;
  margin-top: 6px;
  font-size: 13px;
  color: #aaa;
}

.switch-hint a {
  color: #5a7d96;
  text-decoration: none;
  font-weight: 600;
}

.switch-hint a:hover {
  text-decoration: underline;
  color: #4a6d86;
}

/* 注册第二步：语言等级选择 */
.reg-hint { text-align: center; font-size: 14px; color: #888; margin-bottom: 4px; }

.lang-grid { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-bottom: 14px; }
.lang-opt {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 14px; border-radius: 12px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); cursor: pointer; transition: all 0.2s;
  min-width: 60px;
}
.lang-opt.active { border-color: #5a7d96; background: rgba(90,125,150,0.06); }
.lang-flag { font-size: 24px; }
.lang-name { font-size: 12px; color: #666; font-weight: 500; }

.level-grid { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.level-opt {
  padding: 10px 14px; border-radius: 10px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); font-size: 13px; font-weight: 500;
  color: #666; cursor: pointer; text-align: center; transition: all 0.2s;
}
.level-opt.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); font-weight: 600; }
</style>

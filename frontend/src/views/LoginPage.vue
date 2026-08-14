<script setup>
/**
 * LoginPage.vue — 登录 / 注册一体页
 * 默认显示登录表单，点击"注册新账号"切换到注册表单
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getExamLevels } from '@/data/examLevels'
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

// 密码可见性切换
const showLoginPwd = ref(false)
const showRegPwd = ref(false)
const showRegPwd2 = ref(false)

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
    await authStore.register({
      username: regUsername.value,
      password: regPassword.value,
      nickname: regNickname.value || regUsername.value,
      email: regEmail.value.trim() || undefined,
    })
    // 注册成功后设置语言和等级（setTargetLanguage/setTargetLevel 会自动同步到后端）
    authStore.setTargetLanguage(selectedLang.value)
    authStore.setTargetLevel(selectedLevel.value !== null ? selectedLevel.value : -1)
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
        <div class="login-emojis"><span>📚</span><span>✨</span><span>🌍</span></div>
        <LetterSwapTitle :text="pageTitle" tag="h1" color="#2a2438" />
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
          <div class="pwd-wrap">
            <input v-model="loginPassword" :type="showLoginPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" />
            <button type="button" class="pwd-toggle" :aria-label="showLoginPwd ? '隐藏密码' : '显示密码'" @click="showLoginPwd = !showLoginPwd">
              <svg v-if="!showLoginPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>
        <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
        <button type="submit" class="login-btn btn btn-primary btn-block btn-lg" :disabled="loading">
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
            <div class="pwd-wrap">
              <input v-model="regPassword" :type="showRegPwd ? 'text' : 'password'" placeholder="至少4位密码" autocomplete="new-password" />
              <button type="button" class="pwd-toggle" :aria-label="showRegPwd ? '隐藏密码' : '显示密码'" @click="showRegPwd = !showRegPwd">
                <svg v-if="!showRegPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              </button>
            </div>
          </div>
          <div class="input-group">
            <label>确认密码 *</label>
            <div class="pwd-wrap">
              <input v-model="regPassword2" :type="showRegPwd2 ? 'text' : 'password'" placeholder="再次输入密码" autocomplete="new-password" />
              <button type="button" class="pwd-toggle" :aria-label="showRegPwd2 ? '隐藏密码' : '显示密码'" @click="showRegPwd2 = !showRegPwd2">
                <svg v-if="!showRegPwd2" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              </button>
            </div>
          </div>
        </template>

        <template v-if="step === 2">
          <p class="reg-hint">选择你要学习的语言和等级</p>
          <div class="lang-grid">
            <button v-for="l in langList" :key="l.code"
              class="lang-opt btn" :class="selectedLang === l.code ? 'btn-secondary' : 'btn-ghost'"
              @click.prevent="selectedLang = l.code">
              <span class="lang-flag">{{ l.flag }}</span>
              <span class="lang-name">{{ l.name }}</span>
            </button>
          </div>
          <div class="level-grid">
            <button class="level-opt btn" :class="selectedLevel === -1 ? 'btn-secondary' : 'btn-ghost'" @click.prevent="selectedLevel = -1">全部等级</button>
            <button v-for="lv in examLevels" :key="lv.value"
              class="level-opt btn" :class="selectedLevel === lv.value ? 'btn-secondary' : 'btn-ghost'"
              @click.prevent="selectedLevel = lv.value">
              {{ lv.examLabel }} · {{ lv.examName }}
            </button>
          </div>
        </template>

        <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="login-success">{{ successMsg }}</p>
        <button type="submit" class="login-btn btn btn-primary btn-block btn-lg" :disabled="loading">
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
  background: var(--color-bg-card);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 48px 40px 38px;
  box-shadow: var(--shadow-lg);
  position: relative;
  overflow: hidden;
  animation: cardIn 0.55s var(--ease-out-expo);
}
/* 卡片顶部金色渐变细线装饰 */
.login-card::before {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--color-gold), var(--color-gold-light), var(--color-gold));
  opacity: 0.7;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(18px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.login-header {
  text-align: center;
  margin-bottom: 34px;
}

.login-header :deep(.letter-swap-title) {
  font-size: 30px;
  font-weight: 600;
  font-family: var(--font-heading);
  letter-spacing: 0.3px;
  margin-bottom: 10px;
}

.login-subtitle {
  font-size: 13.5px;
  color: var(--color-text-muted);
  letter-spacing: 0.6px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.input-group label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
  letter-spacing: 0.4px;
}

.input-group input {
  padding: 13px 15px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-hover);
  background: #fff;
  color: var(--color-text);
  font-size: 15px;
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s;
}

.input-group input::placeholder {
  color: var(--color-text-muted);
}

.input-group input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(255, 107, 107, 0.1);
}

/* 密码可见性切换 */
.pwd-wrap { position: relative; }
.pwd-wrap input { width: 100%; padding-right: 44px; }
.pwd-toggle {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  border: none; background: transparent;
  color: var(--color-text-muted);
  border-radius: 8px;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}
.pwd-toggle:hover { color: var(--color-text); background: rgba(42, 36, 56, 0.04); }

.login-error {
  color: #a85a4c;
  font-size: 13px;
  text-align: center;
  background: #fff0ed;
  padding: 9px 14px;
  border-radius: var(--radius-sm);
}

.login-success {
  color: #3fa65a;
  font-size: 13px;
  text-align: center;
  background: #f2fbea;
  padding: 9px 14px;
  border-radius: var(--radius-sm);
}

.login-btn {
  letter-spacing: 1.5px;
}

.switch-hint {
  text-align: center;
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.switch-hint a {
  color: var(--color-gold);
  text-decoration: none;
  font-weight: 600;
}

.switch-hint a:hover {
  text-decoration: underline;
  color: var(--color-gold-deep);
}

/* 注册第二步：语言等级选择 */
.reg-hint { text-align: center; font-size: 14px; color: var(--color-text-secondary); margin-bottom: 6px; }

.lang-grid { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-bottom: 16px; }
.lang-opt {
  display: flex; flex-direction: column; align-items: center; gap: 5px;
  min-width: 66px;
}
.lang-flag { font-size: 24px; }
.lang-name { font-size: 12px; color: var(--color-text-secondary); font-weight: 500; }

.level-grid { display: flex; flex-direction: column; gap: 7px; margin-bottom: 10px; }
.level-opt {
  font-size: 13px; font-weight: 500;
  color: var(--color-text-secondary); cursor: pointer; text-align: center;
}
.level-opt.btn-secondary { color: var(--color-gold); border-color: var(--color-gold); background: rgba(255, 107, 107, 0.06); font-weight: 600; }
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'
import { userApi } from '@/api/user'
import SimplexNoiseBackground from '@/components/effects/SimplexNoiseBackground.vue'
import AISidebar from '@/components/layout/AISidebar.vue'

const router = useRouter()
const authStore = useAuthStore()
const languageStore = useLanguageStore()
const showLangSwitcher = ref(false)

const isAdmin = computed(() => authStore.user?.roles?.includes('ROLE_ADMIN'))

onMounted(() => {
  if (authStore.isLoggedIn) {
    languageStore.fetchLanguages()
  }
})

async function switchLanguage(code) {
  if (!authStore.isLoggedIn || !authStore.user) return
  try {
    await userApi.updateUser({ id: authStore.user.id, currentLangCode: code })
    authStore.setTargetLanguage(code)
    await authStore.fetchProfile()
    showLangSwitcher.value = false
    // 刷新当前页面，使各页面组件根据新语言重新加载数据
    router.go(0)
  } catch (e) { /* ignore */ }
}
</script>

<template>
  <div class="main-layout">
    <SimplexNoiseBackground />

    <!-- 顶部导航栏：极简透明玻璃效果 -->
    <nav class="navbar">
      <div class="navbar-inner">
        <router-link to="/" class="navbar-brand">
          <span class="brand-text">Amazing Teaching</span>
          <span class="brand-tag">多语言学习平台</span>
        </router-link>

        <div class="navbar-links">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/learn" class="nav-link">学习</router-link>
          <router-link to="/practice" class="nav-link">练习</router-link>
          <router-link to="/flashcards" class="nav-link">背单词</router-link>
          <router-link to="/linkage" class="nav-link">诊断</router-link>
          <router-link to="/favorites" class="nav-link">收藏</router-link>
          <router-link to="/history" class="nav-link">记录</router-link>
          <router-link to="/ai" class="nav-link">AI助手</router-link>
          <router-link to="/stats" class="nav-link">分析</router-link>
          <router-link v-if="isAdmin" to="/admin" class="nav-link">管理</router-link>
        </div>

        <div class="navbar-actions">
          <template v-if="authStore.isLoggedIn">
            <!-- 语言切换 -->
            <div class="lang-switch-wrap" v-if="authStore.user?.currentLangCode">
              <button class="lang-switch-btn" @click="showLangSwitcher = !showLangSwitcher">
                {{ authStore.user.currentLangCode?.toUpperCase() }}
              </button>
              <div v-if="showLangSwitcher" class="lang-dropdown">
                <div
                  v-for="l in languageStore.languages" :key="l.code"
                  class="lang-drop-item"
                  :class="{ active: l.code === authStore.user?.currentLangCode }"
                  @click="switchLanguage(l.code)"
                >
                  {{ l.nameCn }}
                </div>
              </div>
            </div>
            <div class="user-info" @click="router.push('/profile')">
              <span class="user-name">{{ authStore.user?.nickname || authStore.user?.username }}</span>
              <button class="nav-avatar">
                {{ (authStore.user?.nickname || authStore.user?.username || 'U')[0].toUpperCase() }}
              </button>
            </div>
          </template>
          <template v-else>
            <button class="nav-btn-login" @click="router.push('/login')">
              登录
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </button>
          </template>
        </div>
      </div>
    </nav>

    <main class="main-content">
      <slot />
    </main>

    <!-- 全局 AI 侧边栏：所有模块可用 -->
    <AISidebar />
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
}

/* ====== 导航栏 ====== */
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.navbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  padding: 0 2.5rem;
  height: 68px;
  gap: 2.5rem;
}

/* 品牌标识 — 左侧圆点 + 文字 */
.navbar-brand {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  text-decoration: none;
  flex-shrink: 0;
}

.brand-dot {
  display: none;
}

.brand-text {
  font-size: 22px;
  font-weight: 700;
  font-style: italic;
  letter-spacing: 1.5px;
  font-family: 'Georgia', 'Times New Roman', serif;
  color: var(--color-text);
  background: linear-gradient(135deg, #5a7d96, #8c5e9e);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 导航链接 */
.navbar-links {
  display: flex;
  gap: 2px;
  flex: 1;
}

.nav-link {
  padding: 8px 18px;
  border-radius: var(--radius-full);
  text-decoration: none;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.1px;
  transition: all 0.3s var(--ease-smooth);
}

.nav-link:hover {
  color: var(--color-text);
  background: rgba(0, 0, 0, 0.04);
}

.nav-link.router-link-active {
  color: var(--color-primary-dark);
  background: rgba(139, 107, 158, 0.08);
  font-weight: 600;
}

/* 右侧操作按钮 */
.navbar-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-shrink: 0;
}

/* 语言切换 */
.lang-switch-wrap {
  position: relative;
}
.lang-switch-btn {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1.5px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.5);
  color: var(--color-text);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.lang-switch-btn:hover {
  background: rgba(0, 0, 0, 0.04);
}
.lang-dropdown {
  position: absolute;
  top: 42px;
  right: 0;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.1);
  padding: 6px 0;
  z-index: 2000;
  min-width: 100px;
}
.lang-drop-item {
  padding: 8px 18px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: background 0.15s;
}
.lang-drop-item:hover { background: #f5f6f8; color: var(--color-text); }
.lang-drop-item.active { color: var(--color-primary-dark); font-weight: 600; }

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius-full);
  transition: background 0.25s;
}
.user-info:hover {
  background: rgba(0, 0, 0, 0.04);
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
}

.nav-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.3s var(--ease-bounce);
}

.nav-avatar:hover {
  transform: scale(1.1);
}

.nav-btn-login {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 22px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--color-primary-dark);
  background: transparent;
  color: var(--color-primary-dark);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s var(--ease-smooth);
}

.nav-btn-login:hover {
  background: var(--color-primary-dark);
  color: #fff;
  transform: translateY(-1px);
}

/* 主体区域 */
.main-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 2rem 2.5rem;
  position: relative;
  z-index: 1;
}

@media (max-width: 768px) {
  .navbar-inner { padding: 0 1.25rem; gap: 1rem; }
  .navbar-links { display: none; }
  .main-content { padding: 1.5rem 1.25rem; }
}
</style>

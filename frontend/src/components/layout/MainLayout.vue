<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'
import { userApi } from '@/api/user'
import AISidebar from '@/components/layout/AISidebar.vue'

const router = useRouter()
const authStore = useAuthStore()
const languageStore = useLanguageStore()
const showLangSwitcher = ref(false)
const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 8
}

const isAdmin = computed(() => authStore.user?.roles?.includes('ROLE_ADMIN'))

onMounted(() => {
  if (authStore.isLoggedIn) {
    languageStore.fetchLanguages()
  }
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})

async function switchLanguage(code) {
  if (!authStore.isLoggedIn || !authStore.user) return
  try {
    await userApi.updateUser({ id: authStore.user.id, currentLangCode: code })
    authStore.setTargetLanguage(code)
    await authStore.fetchProfile()
    showLangSwitcher.value = false
    router.go(0)
  } catch (e) { /* ignore */ }
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="main-layout">
    <!-- 顶部导航：孟菲斯彩色胶囊 -->
    <nav class="navbar" :class="{ scrolled }">
      <div class="navbar-inner">
        <router-link to="/" class="navbar-brand">
          <span class="brand-face">😊</span>
          <span class="brand-text">Amazing Teaching</span>
        </router-link>

        <div class="navbar-links">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/learn" class="nav-link">学习</router-link>
          <router-link to="/flashcards" class="nav-link">背单词</router-link>
          <router-link to="/linkage" class="nav-link">诊断</router-link>
          <router-link to="/favorites" class="nav-link">收藏</router-link>
          <router-link to="/history" class="nav-link">记录</router-link>
          <router-link to="/ai" class="nav-link">AI助手</router-link>
          <router-link to="/stats" class="nav-link">分析</router-link>
          <router-link v-if="isAdmin" to="/admin" class="nav-link nav-link-admin">管理</router-link>
        </div>

        <div class="navbar-actions">
          <template v-if="authStore.isLoggedIn">
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
            <button class="nav-login-btn" @click="router.push('/login')">登录</button>
          </template>
        </div>
      </div>
    </nav>

    <main class="main-content">
      <slot />
    </main>

    <!-- 底部跑马灯 + 页脚 -->
    <div class="marquee-zone" aria-hidden="true">
      <div class="marquee-back"></div>
      <div class="playful-marquee">
        <div class="playful-marquee-track">
        <span>背单词</span><span>学语法</span><span>练阅读</span><span>AI 写作</span><span>每日打卡</span><span>开心学习</span>
        <span>背单词</span><span>学语法</span><span>练阅读</span><span>AI 写作</span><span>每日打卡</span><span>开心学习</span>
        </div>
      </div>
    </div>

    <footer class="playful-footer">
      <div class="wrap">
        <span>© 2025 Amazing Teaching · 今天也要开心学习鸭 🦆</span>
        <button class="to-top" @click="scrollTop">回到顶部 ↑</button>
      </div>
    </footer>

    <!-- 全局 AI 侧边栏：所有模块可用 -->
    <AISidebar />
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ====== 导航栏：奶油实底贴纸 + 彩色胶囊（无玻璃模糊） ====== */
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: #fff6e9;
  border-bottom: 3px solid #2a2438;
  transition: box-shadow 0.25s var(--ease-smooth), background 0.25s ease;
}
.navbar.scrolled {
  background: #fff6e9;
  box-shadow: 0 6px 0 rgba(42, 36, 56, 0.12);
}

.navbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  padding: 12px 2.5rem;
  min-height: 76px;
  gap: 2rem;
}

.navbar-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}

.brand-face {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  background: #ffd93d;
  border: 3px solid #2a2438;
  border-radius: 50%;
  box-shadow: 3px 4px 0 #2a2438;
  animation: face-wobble 3.4s ease-in-out infinite;
}

@keyframes face-wobble {
  0%, 100% { transform: rotate(0); }
  15% { transform: rotate(13deg); }
  30% { transform: rotate(-9deg); }
  45% { transform: rotate(6deg); }
  60% { transform: rotate(0); }
}

.brand-text {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #2a2438;
}

.navbar-links {
  display: flex;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.nav-link {
  padding: 8px 14px;
  border-radius: 999px;
  text-decoration: none;
  color: #2a2438;
  font-size: 14px;
  font-weight: 800;
  border: 3px solid transparent;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.nav-link:hover {
  background: #ffd93d;
  border-color: #2a2438;
  box-shadow: 2px 3px 0 #2a2438;
  transform: rotate(-3deg) scale(1.04);
}

.nav-link.router-link-active {
  background: #ff6b6b;
  color: #fff;
  border-color: #2a2438;
  box-shadow: 2px 3px 0 #2a2438;
}

.nav-link-admin {
  background: #fff;
}

.navbar-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
}

.lang-switch-wrap {
  position: relative;
}

.lang-switch-btn {
  font-family: inherit;
  font-weight: 800;
  font-size: 13px;
  color: #fff;
  background: #4d96ff;
  border: 3px solid #2a2438;
  border-radius: 999px;
  padding: 8px 14px;
  box-shadow: 2px 3px 0 #2a2438;
  transition: all 0.2s;
}
.lang-switch-btn:hover {
  transform: translateY(-2px) rotate(-2deg);
}

.lang-dropdown {
  position: absolute;
  top: 48px;
  right: 0;
  background: #fff;
  border: 3px solid #2a2438;
  border-radius: 18px;
  box-shadow: 5px 6px 0 #2a2438;
  padding: 6px 0;
  z-index: 2000;
  min-width: 116px;
}
.lang-drop-item {
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 700;
  color: rgba(42, 36, 56, 0.72);
  cursor: pointer;
  transition: background 0.15s;
}
.lang-drop-item:hover {
  background: #fff7e8;
  color: #2a2438;
}
.lang-drop-item.active {
  color: #ff6b6b;
  font-weight: 800;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  border: 3px solid transparent;
  transition: all 0.2s;
}
.user-info:hover {
  background: #fff;
  border-color: #2a2438;
  transform: rotate(-1deg);
}

.user-name {
  font-size: 14px;
  font-weight: 800;
  color: #2a2438;
  white-space: nowrap;
}

.nav-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 3px solid #2a2438;
  background: #9b5de5;
  color: #fff;
  font-weight: 800;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 2px 3px 0 #2a2438;
  transition: transform 0.2s;
}
.nav-avatar:hover {
  transform: scale(1.07) rotate(6deg);
}

.nav-login-btn {
  font-family: inherit;
  font-weight: 800;
  font-size: 14px;
  background: #ffd93d;
  color: #2a2438;
  border: 3px solid #2a2438;
  border-radius: 999px;
  padding: 9px 18px;
  box-shadow: 3px 4px 0 #2a2438;
  transition: all 0.2s;
}
.nav-login-btn:hover {
  transform: translateY(-2px) rotate(-2deg);
}

/* 主体区域 */
.main-content {
  flex: 1;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 2rem 2.5rem 3rem;
  position: relative;
  z-index: 1;
}

/* 底部 */
.playful-footer {
  position: relative;
  z-index: 2;
  padding: 20px 0 30px;
  background: var(--color-bg-main);
}

.playful-footer .wrap {
  width: min(1240px, 92%);
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-weight: 800;
  color: rgba(42, 36, 56, 0.72);
}

.to-top {
  font-family: inherit;
  font-weight: 800;
  background: #6bcb77;
  color: #fff;
  border: 3px solid #2a2438;
  border-radius: 999px;
  padding: 8px 18px;
  box-shadow: 3px 4px 0 #2a2438;
  transition: all 0.2s;
}
.to-top:hover {
  transform: translateY(-2px) rotate(2deg);
}
.to-top:active {
  transform: translateY(3px);
  box-shadow: 0 1px 0 #2a2438;
}

/* 中等屏：导航链接换行后横向滚动，避免挤成一团 */
@media (max-width: 1180px) {
  .navbar-inner {
    flex-wrap: wrap;
    padding-top: 10px;
    padding-bottom: 6px;
    gap: 0.9rem;
  }
  .navbar-links {
    order: 3;
    flex-basis: 100%;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 8px;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;
    mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
    -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
  }
  .navbar-links::-webkit-scrollbar {
    display: none;
  }
  .nav-link {
    flex-shrink: 0;
  }
}

@media (max-width: 860px) {
  .navbar-inner {
    padding: 10px 1.1rem;
    gap: 0.8rem;
    flex-wrap: wrap;
  }
  .navbar-links {
    order: 3;
    flex-basis: 100%;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 6px;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;
    mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
    -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 16px, #000 calc(100% - 16px), transparent 100%);
  }
  .navbar-links::-webkit-scrollbar {
    display: none;
  }
  .main-content {
    padding: 1.5rem 1.1rem 2rem;
  }
}
</style>

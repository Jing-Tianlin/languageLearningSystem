<script setup>
/**
 * App.vue — 根组件
 *
 * 职责：
 * 1. 跟随鼠标的奶油色光斑（rAF 插值，直接操作 DOM，避免高频响应式开销）
 * 2. 按路由 meta.layout 切换布局
 * 3. 挂载全局 Toast 通知容器
 * 4. 页面切换过渡动画（缩放 + 旋转 + 上移入场）
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import MainLayout from '@/components/layout/MainLayout.vue'
import ToastContainer from '@/components/common/ToastContainer.vue'
import MeshGradientBackground from '@/components/effects/MeshGradientBackground.vue'

const route = useRoute()
const authStore = useAuthStore()

const layout = computed(() => route.meta.layout || 'main')

/* ===== 奶油色光斑：rAF 平滑跟随 ===== */
const glowEl = ref(null)
let rafId = 0
let targetX = -500
let targetY = -500
let curX = -500
let curY = -500
let glowEnabled = false

function onMouseMove(e) {
  targetX = e.clientX
  targetY = e.clientY
}

function tick() {
  // 弹性插值：柔和跟随，不贴手
  curX += (targetX - curX) * 0.12
  curY += (targetY - curY) * 0.12
  if (glowEl.value) {
    glowEl.value.style.transform = `translate(${curX - 170}px, ${curY - 170}px)`
    if (!glowEl.value.classList.contains('is-ready')) {
      glowEl.value.classList.add('is-ready')
    }
  }
  rafId = requestAnimationFrame(tick)
}

const reducedMotion = typeof window !== 'undefined'
  && window.matchMedia
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const hasMouse = typeof window !== 'undefined'
  && window.matchMedia
  && window.matchMedia('(hover: hover) and (pointer: fine)').matches

onMounted(() => {
  glowEnabled = hasMouse && !reducedMotion
  if (glowEnabled) {
    window.addEventListener('mousemove', onMouseMove, { passive: true })
    rafId = requestAnimationFrame(tick)
  }
  // 已登录但用户信息未加载时，自动获取用户资料
  if (authStore.isLoggedIn && !authStore.user) {
    authStore.fetchProfile()
  }
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  cancelAnimationFrame(rafId)
})
</script>

<template>
  <MeshGradientBackground />
  <div v-if="glowEnabled" ref="glowEl" class="playful-cursor-blob" aria-hidden="true"></div>
  <ToastContainer />
  <transition name="layout" mode="out-in">
    <AuthLayout v-if="layout === 'auth'">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </AuthLayout>
    <MainLayout v-else>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </MainLayout>
  </transition>
</template>

<style>
/* 页面切换动画：轻微缩放 + 旋转 + 上移入场 */
.page-enter-active, .page-leave-active {
  transition: all 0.34s var(--ease-bounce, cubic-bezier(0.34, 1.56, 0.64, 1));
}
.page-enter-from {
  opacity: 0;
  transform: translateY(22px) scale(0.96) rotate(-1deg);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(0.98);
}

/* 布局切换动画 */
.layout-enter-active, .layout-leave-active {
  transition: opacity 0.25s ease;
}
.layout-enter-from, .layout-leave-to {
  opacity: 0;
}
</style>

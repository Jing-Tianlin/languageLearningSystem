<script setup>
/**
 * App.vue — 根组件
 *
 * 职责：
 * 1. 监听全局鼠标移动 → 存入 UI Store
 * 2. 按路由 meta.layout 切换布局
 * 3. 挂载全局 Toast 通知容器
 * 4. 页面切换过渡动画
 */
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import MainLayout from '@/components/layout/MainLayout.vue'
import ToastContainer from '@/components/common/ToastContainer.vue'
import MeshGradientBackground from '@/components/effects/MeshGradientBackground.vue'

const route = useRoute()
const uiStore = useUiStore()
const authStore = useAuthStore()

const layout = computed(() => route.meta.layout || 'main')

function onMouseMove(e) {
  uiStore.updateMousePosition(e.clientX, e.clientY)
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove, { passive: true })
  // 已登录但用户信息未加载时，自动获取用户资料
  if (authStore.isLoggedIn && !authStore.user) {
    authStore.fetchProfile()
  }
})
onUnmounted(() => window.removeEventListener('mousemove', onMouseMove))
</script>

<template>
  <MeshGradientBackground />
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
/* 页面切换动画 */
.page-enter-active, .page-leave-active {
  transition: all 0.3s var(--ease-smooth, ease);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 布局切换动画 */
.layout-enter-active, .layout-leave-active {
  transition: opacity 0.25s ease;
}
.layout-enter-from, .layout-leave-to {
  opacity: 0;
}
</style>

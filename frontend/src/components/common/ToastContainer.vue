<script setup>
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()

const iconMap = {
  success: '✓',
  error: '✕',
  warning: '▲',
  info: '●',
}

const typeClass = {
  success: 'toast-success',
  error: 'toast-error',
  warning: 'toast-warning',
  info: 'toast-info',
}
</script>

<template>
  <div class="toast-container">
    <transition-group name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="toast-item"
        :class="[typeClass[t.type], { 'toast-exit': !t.show }]"
      >
        <span class="toast-icon">{{ iconMap[t.type] }}</span>
        <span class="toast-message">{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
  backdrop-filter: blur(12px);
  pointer-events: auto;
  min-width: 200px;
  max-width: 400px;
  transition: all 0.3s ease;
}

.toast-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.toast-success { background: rgba(240, 250, 243, 0.95); color: #27ae60; border: 1px solid #d4edda; }
.toast-success .toast-icon { background: #27ae60; color: #fff; }

.toast-error { background: rgba(254, 245, 245, 0.95); color: #e74c3c; border: 1px solid #f8d7da; }
.toast-error .toast-icon { background: #e74c3c; color: #fff; }

.toast-warning { background: rgba(255, 248, 225, 0.95); color: #f39c12; border: 1px solid #ffe082; }
.toast-warning .toast-icon { background: #f39c12; color: #fff; }

.toast-info { background: rgba(248, 250, 251, 0.95); color: #5a7d96; border: 1px solid #e0e0e0; }
.toast-info .toast-icon { background: #5a7d96; color: #fff; }

/* 动画 */
.toast-enter-active, .toast-leave-active { transition: all 0.35s ease; }
.toast-enter-from { opacity: 0; transform: translateY(-20px) scale(0.95); }
.toast-leave-to { opacity: 0; transform: translateY(-10px) scale(0.95); }
.toast-exit { opacity: 0; transform: translateY(-10px) scale(0.95); }
</style>

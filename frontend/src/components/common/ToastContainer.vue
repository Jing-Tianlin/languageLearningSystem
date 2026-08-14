<script setup>
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()

const iconMap = {
  success: '✓',
  error: '✕',
  warning: '!',
  info: 'i',
}

const typeClass = {
  success: 'toast-success',
  error: 'toast-error',
  warning: 'toast-warning',
  info: 'toast-info',
}
</script>

<template>
  <div class="toast-container" aria-live="polite">
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
/* Playful Memphis Toast：白底贴纸 + 粗描边 + 硬投影 */
.toast-container {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px 12px 12px;
  background: #fff;
  border: 3px solid #2a2438;
  border-radius: 18px;
  box-shadow: 4px 5px 0 #2a2438;
  font-size: 14px;
  font-weight: 800;
  color: #2a2438;
  letter-spacing: 0.01em;
  pointer-events: auto;
  min-width: 200px;
  max-width: min(420px, 90vw);
}

.toast-icon {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  flex-shrink: 0;
  border: 2px solid #2a2438;
  box-shadow: 1px 2px 0 #2a2438;
}

.toast-success .toast-icon { background: #6bcb77; color: #fff; }
.toast-error .toast-icon { background: #ff6b6b; color: #fff; }
.toast-warning .toast-icon { background: #ffd93d; color: #2a2438; }
.toast-info .toast-icon { background: #4d96ff; color: #fff; }

/* 入场：贴纸 Q 弹落下 */
.toast-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.toast-leave-active {
  transition: all 0.28s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(-26px) scale(0.9) rotate(-2deg);
}
.toast-leave-to,
.toast-exit {
  opacity: 0;
  transform: translateY(-14px) scale(0.94);
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active {
    transition: none;
  }
}
</style>

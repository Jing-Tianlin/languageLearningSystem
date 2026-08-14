<script setup>
defineProps({
  icon: { type: String, default: 'inbox' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
})

defineEmits(['action'])
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon"><span class="icon-svg" :class="icon" /></div>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <button
      v-if="actionText"
      class="btn btn-primary"
      @click="$emit('action')"
    >
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
/* Playful Memphis 空状态：中央贴纸卡 + 圆形图标底座 + 浮动动画 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 30px;
  text-align: center;
  background: #fff;
  border: 3px solid #2a2438;
  border-radius: 24px;
  box-shadow: 6px 7px 0 #2a2438;
  max-width: 560px;
  margin: 18px auto;
}

.empty-icon {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  margin-bottom: 18px;
  font-size: 34px;
  background: #ffd93d;
  border: 3px solid #2a2438;
  border-radius: 50%;
  box-shadow: 3px 4px 0 #2a2438;
  transform: rotate(-6deg);
  animation: empty-bob 3.4s ease-in-out infinite;
}

.empty-icon :deep(.icon-svg::after) {
  background: #2a2438;
}

.empty-title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #2a2438;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 14px;
  color: rgba(42, 36, 56, 0.65);
  margin: 0 0 20px;
  max-width: 320px;
  line-height: 1.7;
}

@keyframes empty-bob {
  0%, 100% { transform: rotate(-6deg) translateY(0); }
  50% { transform: rotate(4deg) translateY(-8px); }
}

@media (prefers-reduced-motion: reduce) {
  .empty-icon {
    animation: none;
  }
}
</style>

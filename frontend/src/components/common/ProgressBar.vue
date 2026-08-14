<script setup>
defineProps({
  percent: { type: Number, default: 0 },
  label: { type: String, default: '' },
})
</script>

<template>
  <div class="progress-bar-wrap">
    <div v-if="label" class="progress-bar-header">
      <span class="progress-bar-label">{{ label }}</span>
      <span class="progress-bar-percent">{{ Math.round(percent) }}%</span>
    </div>
    <div
      class="progress-bar-track"
      role="progressbar"
      :aria-valuemin="0"
      :aria-valuemax="100"
      :aria-valuenow="Math.round(percent)"
      :aria-label="label || '进度'"
    >
      <div class="progress-bar-fill" :style="{ width: Math.min(100, Math.max(0, percent)) + '%' }" />
    </div>
  </div>
</template>

<style scoped>
/* Playful Memphis 进度条：白底粗边框 + 黄→珊瑚红渐变 */
.progress-bar-wrap {
  width: 100%;
}

.progress-bar-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}

.progress-bar-label {
  font-size: 13px;
  color: rgba(42, 36, 56, 0.6);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.progress-bar-percent {
  font-size: 14px;
  font-weight: 800;
  color: #2a2438;
  font-variant-numeric: tabular-nums;
}

.progress-bar-track {
  width: 100%;
  height: 18px;
  border-radius: 999px;
  background: #fff;
  border: 3px solid #2a2438;
  box-shadow: 2px 3px 0 rgba(42, 36, 56, 0.9);
  padding: 2px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 999px;
  border: 2px solid rgba(42, 36, 56, 0.85);
  background: linear-gradient(90deg, #ffd93d, #ff6b6b);
  transition: width 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@media (prefers-reduced-motion: reduce) {
  .progress-bar-fill {
    transition: none;
  }
}
</style>

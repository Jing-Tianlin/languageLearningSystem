<script setup>
defineProps({
  percent: { type: Number, default: 0 },
  label: { type: String, default: '' },
})
</script>

<template>
  <div class="progress-bar-wrap">
    <div class="progress-bar-header">
      <span class="progress-bar-label">{{ label }}</span>
      <span class="progress-bar-percent">{{ Math.round(percent) }}%</span>
    </div>
    <div class="progress-bar-track">
      <div class="progress-bar-fill" :style="{ width: percent + '%' }" />
    </div>
  </div>
</template>

<style scoped>
.progress-bar-wrap {
  width: 100%;
}
.progress-bar-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.progress-bar-label {
  font-size: 13px;
  color: rgba(26, 16, 40, 0.5);
  font-weight: 500;
}
.progress-bar-percent {
  font-size: 14px;
  font-weight: 700;
  color: #7c5cbf;
}
.progress-bar-track {
  width: 100%;
  height: 8px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.progress-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #7c5cbf, #f0975c);
  transition: width 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
</style>

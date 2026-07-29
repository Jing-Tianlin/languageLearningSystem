<script setup>
defineProps({
  icon: { type: String, default: '' },
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '' },
})
</script>

<template>
  <div class="stats-card">
    <span v-if="icon" class="stats-icon icon-svg" :class="icon"></span>
    <div class="stats-body">
      <span class="stats-value">{{ value }}</span>
      <span class="stats-label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.stats-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  transition: transform 0.35s ease, box-shadow 0.35s ease, border-color 0.35s ease;
}
.stats-card:hover {
  transform: translateY(-3px);
  border-color: rgba(124, 92, 191, 0.2);
  box-shadow: 0 8px 28px rgba(124, 92, 191, 0.12);
}
.stats-icon {
  width: 28px; height: 28px;
  flex-shrink: 0;
}
.stats-body { display: flex; flex-direction: column; }
.stats-value { font-size: 22px; font-weight: 800; color: #1a1028; font-family: var(--font-heading); }
.stats-label { font-size: 13px; color: rgba(26, 16, 40, 0.4); margin-top: 2px; }
</style>

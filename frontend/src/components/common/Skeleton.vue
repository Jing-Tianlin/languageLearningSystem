<script setup>
defineProps({
  rows: { type: Number, default: 3 },
  columns: { type: Number, default: 1 },
})
</script>

<template>
  <div class="skeleton">
    <div
      v-for="c in columns"
      :key="c"
      class="skeleton-col"
    >
      <div
        v-for="r in rows"
        :key="r"
        class="skeleton-row"
        :style="{ width: r % 3 === 0 ? '60%' : r % 3 === 1 ? '85%' : '100%' }"
      >
        <div class="skeleton-shimmer" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  display: grid;
  gap: 16px;
  padding: 8px 0;
}
.skeleton-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.skeleton-row {
  height: 16px;
  border-radius: 8px;
  background: rgba(0,0,0,0.04);
  overflow: hidden;
  position: relative;
}
.skeleton-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.5) 50%, transparent 100%);
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}
</style>

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
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
  opacity: 0.5;
}
.empty-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 6px;
}
.empty-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0 0 16px;
  max-width: 300px;
  line-height: 1.6;
}
.empty-action {
  padding: 10px 24px;
  border-radius: 10px;
  border: 1.5px solid #5a7d96;
  background: transparent;
  color: #5a7d96;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
}
.empty-action:hover {
  background: rgba(90,125,150,0.08);
}
</style>

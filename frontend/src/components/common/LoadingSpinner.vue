<script setup></script>

<template>
  <div class="loading-spinner">
    <div class="spinner" />
    <p>加载中...</p>
  </div>
</template>

<style scoped>
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  gap: 16px;
  color: rgba(42, 36, 56, 0.4);
}
.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(0, 0, 0, 0.06);
  border-top-color: var(--color-primary, #ff6b6b);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

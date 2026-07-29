<script setup>
</script>

<template>
  <div class="auth-layout">
    <div class="auth-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.auth-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
  padding: 2rem;
}
</style>

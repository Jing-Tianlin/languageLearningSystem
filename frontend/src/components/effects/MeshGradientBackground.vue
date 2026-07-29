<template>
  <div class="mesh-gradient-bg" aria-hidden="true">
    <div class="mesh-blob blob-1"></div>
    <div class="mesh-blob blob-2"></div>
    <div class="mesh-blob blob-3"></div>
    <div class="mesh-blob blob-4"></div>
    <div class="mesh-noise"></div>
  </div>
</template>

<style scoped>
.mesh-gradient-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: var(--color-bg-main, #f4f1ea);
}

.mesh-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  will-change: transform;
}

.blob-1 {
  width: 60vw;
  height: 60vw;
  background: radial-gradient(circle at 45% 45%, rgba(224, 216, 200, 0.85) 0%, transparent 60%);
  top: -18%;
  left: -12%;
  animation: drift-1 32s ease-in-out infinite alternate;
}

.blob-2 {
  width: 55vw;
  height: 55vw;
  background: radial-gradient(circle at 50% 50%, rgba(200, 210, 196, 0.7) 0%, transparent 58%);
  top: 5%;
  right: -18%;
  animation: drift-2 40s ease-in-out infinite alternate;
}

.blob-3 {
  width: 50vw;
  height: 50vw;
  background: radial-gradient(circle at 40% 60%, rgba(232, 221, 208, 0.75) 0%, transparent 55%);
  bottom: -15%;
  left: 10%;
  animation: drift-3 36s ease-in-out infinite alternate;
}

.blob-4 {
  width: 45vw;
  height: 45vw;
  background: radial-gradient(circle at 55% 40%, rgba(216, 208, 192, 0.7) 0%, transparent 56%);
  bottom: 10%;
  right: 0%;
  animation: drift-4 44s ease-in-out infinite alternate;
}

@keyframes drift-1 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(10%, 14%) scale(1.1); }
}

@keyframes drift-2 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-12%, 10%) scale(1.08); }
}

@keyframes drift-3 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(8%, -12%) scale(1.09); }
}

@keyframes drift-4 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-10%, -8%) scale(1.06); }
}

/* 极淡纸张噪点，增加材质感 */
.mesh-noise {
  position: absolute;
  inset: 0;
  opacity: 0.03;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .mesh-blob {
    animation: none;
  }
}
</style>

<script setup>
</script>

<template>
  <div class="auth-layout">
    <div class="auth-content">
      <slot />
    </div>

    <aside class="auth-aside">
      <span class="auth-emoji e1">🎉</span>
      <span class="auth-emoji e2">⭐</span>
      <span class="auth-emoji e3">💛</span>
      <span class="auth-emoji e4">🎈</span>

      <div class="auth-aside-card">
        <p class="auth-kicker">AMAZING TEACHING · 2025</p>
        <h2>学语言，<br />也可以很好玩！</h2>
        <p>词汇、语法、AI 助手，都做成让你会心一笑的小游戏。</p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.auth-layout {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 36vw);
  align-items: stretch;
}

.auth-content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

/* 右侧明黄孟菲斯面板 */
.auth-aside {
  position: relative;
  z-index: 1;
  background: #ffd93d;
  border-left: 3px solid #2a2438;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  overflow: hidden;
}

.auth-aside::before,
.auth-aside::after {
  content: "";
  position: absolute;
  border-radius: 50%;
}

.auth-aside::before {
  width: 240px;
  height: 240px;
  top: -70px;
  right: -70px;
  background: #ff6b6b;
  border: 3px solid #2a2438;
  box-shadow: 8px 9px 0 #2a2438;
}

.auth-aside::after {
  width: 170px;
  height: 170px;
  bottom: -55px;
  left: -50px;
  background: #6bcb77;
  border: 3px solid #2a2438;
}

.auth-emoji {
  position: absolute;
  font-size: 36px;
  animation: float 3.8s ease-in-out infinite;
  z-index: 2;
}
.e1 { top: 16%; left: 10%; animation-delay: 0.2s; }
.e2 { top: 12%; right: 12%; animation-delay: 0.9s; }
.e3 { bottom: 16%; left: 16%; animation-delay: 1.5s; }
.e4 { bottom: 20%; right: 10%; animation-delay: 2.1s; }

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(-4deg); }
  50% { transform: translateY(-14px) rotate(7deg); }
}

.auth-aside-card {
  position: relative;
  z-index: 2;
  max-width: 360px;
  background: #fff;
  border: 3px solid #2a2438;
  border-radius: 26px;
  box-shadow: 9px 10px 0 #2a2438;
  padding: 30px 28px;
  transform: rotate(-2deg);
}

.auth-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  color: #ff6b6b;
  margin-bottom: 14px;
}

.auth-aside-card h2 {
  font-family: 'Arial Rounded MT Bold', ui-rounded, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: clamp(26px, 3vw, 40px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.03em;
  margin: 0 0 14px;
  color: #2a2438;
}

.auth-aside-card p:last-child {
  margin: 0;
  font-weight: 700;
  line-height: 1.7;
  color: rgba(42, 36, 56, 0.68);
}

@media (max-width: 900px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }
  .auth-aside {
    display: none;
  }
}
</style>

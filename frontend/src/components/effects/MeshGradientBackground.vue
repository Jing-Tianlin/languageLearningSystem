<template>
  <div class="playful-bg" aria-hidden="true">
    <!-- 柔和彩色 blobs -->
    <div class="blob blob-coral"></div>
    <div class="blob blob-sky"></div>
    <div class="blob blob-mint"></div>

    <!-- 孟菲斯几何贴纸：星星 / 圆点 / 三角 / 小花 -->
    <div class="sticker sticker-star">✦</div>
    <div class="sticker sticker-dot">●</div>
    <div class="sticker sticker-tri">▲</div>
    <div class="sticker sticker-flower">✿</div>

    <!-- 孟菲斯线条涂鸦：波浪 / 十字 / 圆环 / 点半阵 -->
    <svg class="memphis-doodle doodle-squiggle" width="140" height="40" viewBox="0 0 140 40">
      <path d="M4 22 q 12 -20 24 0 t 24 0 t 24 0 t 24 0 t 24 0" fill="none" stroke="#ff6b6b" stroke-width="6" stroke-linecap="round" />
    </svg>
    <svg class="memphis-doodle doodle-cross" width="46" height="46" viewBox="0 0 46 46">
      <path d="M23 5 V41 M5 23 H41" stroke="#9b5de5" stroke-width="8" stroke-linecap="round" />
    </svg>
    <svg class="memphis-doodle doodle-ring" width="58" height="58" viewBox="0 0 58 58">
      <circle cx="29" cy="29" r="20" fill="none" stroke="#6bcb77" stroke-width="8" />
    </svg>
    <svg class="memphis-doodle doodle-arc" width="72" height="40" viewBox="0 0 72 40">
      <path d="M6 36 a 30 30 0 0 1 60 0" fill="none" stroke="#4d96ff" stroke-width="7" stroke-linecap="round" />
    </svg>
    <div class="memphis-doodle doodle-dots"></div>
  </div>
</template>

<style scoped>
/* ===== Playful Memphis 背景：奶油底 + 漂浮色块 + 几何涂鸦贴纸 ===== */
.playful-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: var(--color-bg-main, #fff6e9);
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(46px);
  will-change: transform;
  opacity: 0.5;
}

.blob-coral {
  width: 48vw;
  height: 48vw;
  background: rgba(255, 107, 107, 0.3);
  top: -14%;
  left: -10%;
  animation: bob-a 9s ease-in-out infinite alternate;
}

.blob-sky {
  width: 42vw;
  height: 42vw;
  background: rgba(77, 150, 255, 0.24);
  top: 4%;
  right: -14%;
  animation: bob-b 11s ease-in-out infinite alternate;
}

.blob-mint {
  width: 46vw;
  height: 46vw;
  background: rgba(107, 203, 119, 0.26);
  bottom: -16%;
  left: 14%;
  animation: bob-c 10s ease-in-out infinite alternate;
}

.sticker {
  position: absolute;
  z-index: 1;
  font-weight: 800;
  color: var(--color-text);
  text-shadow: 3px 3px 0 rgba(255, 107, 107, 0.28);
  user-select: none;
  opacity: 0.85;
}

.sticker-star {
  top: 13%;
  left: 4%;
  font-size: 110px;
  color: #6bcb77;
  animation: spin 16s linear infinite;
}

.sticker-dot {
  bottom: 11%;
  right: 5%;
  font-size: 84px;
  color: #ff6b6b;
  animation: spin 20s linear infinite reverse;
}

.sticker-tri {
  top: 52%;
  right: 3%;
  font-size: 44px;
  color: #4d96ff;
  animation: bob-y 4.6s ease-in-out infinite;
}

.sticker-flower {
  bottom: 26%;
  left: 3%;
  font-size: 48px;
  color: #9b5de5;
  animation: bob-y 5.4s ease-in-out infinite reverse;
}

/* ===== 几何涂鸦贴纸 ===== */
.memphis-doodle {
  position: absolute;
  z-index: 1;
  opacity: 0.55;
  user-select: none;
}

.doodle-squiggle {
  top: 24%;
  right: 8%;
  transform: rotate(-8deg);
  animation: bob-y 6s ease-in-out infinite;
}

.doodle-cross {
  bottom: 20%;
  left: 8%;
  transform: rotate(12deg);
  animation: spin 22s linear infinite;
}

.doodle-ring {
  top: 64%;
  left: 12%;
  transform: rotate(-6deg);
  animation: bob-y 7s ease-in-out infinite reverse;
}

.doodle-arc {
  top: 9%;
  right: 22%;
  transform: rotate(6deg);
  animation: bob-y 5.2s ease-in-out infinite;
}

/* 点半阵（halftone dots） */
.doodle-dots {
  width: 120px;
  height: 84px;
  bottom: 34%;
  right: 12%;
  transform: rotate(-4deg);
  background-image: radial-gradient(circle, rgba(42, 36, 56, 0.5) 2.6px, transparent 2.8px);
  background-size: 16px 16px;
  animation: bob-y 8s ease-in-out infinite;
}

@keyframes bob-a {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(9%, 12%) scale(1.1); }
}
@keyframes bob-b {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-10%, 9%) scale(1.08); }
}
@keyframes bob-c {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(7%, -10%) scale(1.09); }
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
@keyframes bob-y {
  0%, 100% { transform: translateY(0) rotate(0); }
  50% { transform: translateY(-24px) rotate(10deg); }
}

/* 小屏减少装饰，避免干扰阅读 */
@media (max-width: 700px) {
  .doodle-squiggle,
  .doodle-dots,
  .doodle-arc {
    display: none;
  }
  .blob {
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .blob,
  .sticker,
  .memphis-doodle {
    animation: none !important;
  }
}
</style>

<script setup>
/**
 * ImageTrail.vue — 鼠标跟随轨迹 (速度散开 + 延迟回收版)
 *
 * 行为：
 * - 鼠标移动越快 → 元素散开越远 (spread ∝ speed)
 * - 鼠标停下来 → 保持散开约 1 秒，再缓慢聚拢 (speed 慢衰减)
 * - 速度衰减极慢（0.98/帧），约 1s 后才开始明显收拢
 */
import { ref, onMounted, onUnmounted } from 'vue'

const ICONS = [
  '/trail-images/cat.svg',
  '/trail-images/rabbit.svg',
  '/trail-images/dog.svg',
  '/trail-images/star.svg',
  '/trail-images/sakura.svg',
  '/trail-images/bird.svg',
]

const COUNT = 6
const dots = ref(
  Array.from({ length: COUNT }, (_, i) => ({
    id: i,
    x: 0,
    y: 0,
    icon: ICONS[i],
    stiffness: 0.06 + i * 0.05,
    angle: (i / COUNT) * Math.PI * 2,
  })),
)

let px = 0
let py = 0
let speed = 0

// 定时器：鼠标停止后延迟重置 speed
let stillTimer = null
const STILL_DELAY = 1200 // 停止 1.2 秒后才开始收拢

let animId = null
let lastTs = 0

function animate(ts) {
  lastTs = ts

  const mx = mouse.value.x
  const my = mouse.value.y

  // 慢衰减：每帧只衰减 2%，约 50 帧(1s 后)还剩 36%
  speed *= 0.98

  const spread = 4 + speed * 50

  dots.value.forEach((d) => {
    const targetX = mx + Math.cos(d.angle) * spread
    const targetY = my + Math.sin(d.angle) * spread

    d.x += (targetX - d.x) * (1 - d.stiffness)
    d.y += (targetY - d.y) * (1 - d.stiffness)

    d.angle += 0.018
  })

  animId = requestAnimationFrame(animate)
}

const mouse = ref({ x: 0, y: 0 })

function onMouseMove(e) {
  const cx = e.clientX
  const cy = e.clientY

  const dx = cx - px
  const dy = cy - py
  const frameSpeed = Math.sqrt(dx * dx + dy * dy)

  // 速度更新权重偏向新值，快速响应加速
  speed = speed * 0.6 + frameSpeed * 0.4

  px = cx
  py = cy
  mouse.value = { x: cx, y: cy }

  // 每次移动重置定时器 — 停止移动后 delay ms 才触发额外衰减
  clearTimeout(stillTimer)
  stillTimer = setTimeout(() => {
    // 1.2s 未移动 → 加速衰减，元素聚拢
    const decay = setInterval(() => {
      speed *= 0.85
      if (speed < 0.5) {
        clearInterval(decay)
        speed = 0
      }
    }, 100)
  }, STILL_DELAY)
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove, { passive: true })
  animId = requestAnimationFrame(animate)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  cancelAnimationFrame(animId)
  clearTimeout(stillTimer)
})
</script>

<template>
  <div class="trail-layer">
    <img
      v-for="d in dots"
      :key="d.id"
      :src="d.icon"
      class="trail-dot"
      :style="{
        left: d.x + 'px',
        top: d.y + 'px',
        opacity: 0.9 - d.stiffness * 1.6,
        width: (40 - d.stiffness * 35) + 'px',
        height: (40 - d.stiffness * 35) + 'px',
        zIndex: COUNT - d.id,
      }"
    />
  </div>
</template>

<style scoped>
.trail-layer {
  position: fixed;
  inset: 0;
  z-index: 9998;
  pointer-events: none;
  overflow: hidden;
}

.trail-dot {
  position: absolute;
  transform: translate(-50%, -50%);
  will-change: left, top;
}
</style>

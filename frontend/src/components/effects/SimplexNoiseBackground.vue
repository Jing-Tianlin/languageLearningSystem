<script setup>
/**
 * SimplexNoiseBackground.vue
 * 全屏动态渐变背景组件
 *
 * 功能:
 * - 使用 Simplex Noise 算法生成流动的渐变背景纹理
 * - 蓝紫 (#6a4475) → 粉橙 (#f5a623) 渐变
 * - 鼠标移动时产生微妙互动：颜色偏移 ±10% + 纹理密度变化 ±20%
 * - 目标 60fps，全屏覆盖
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { createNoise3D } from 'simplex-noise'
import { useUiStore } from '@/stores/ui'
import { renderNoiseFrame } from '@/composables/useSimplexNoise'

const uiStore = useUiStore()
// Canvas DOM 引用
const canvasRef = ref(null)
// simplex-noise 3D 实例
let simplex = null
// requestAnimationFrame ID
let animId = null
// 动画起始时间戳
let startTime = null

/** 响应窗口大小变化，重设 Canvas 尺寸 */
function resize() {
  const c = canvasRef.value
  if (!c) return
  c.width = window.innerWidth
  c.height = window.innerHeight
}

/**
 * 动画主循环 (requestAnimationFrame)
 * - time 参数驱动噪声的 z 轴，产生流动感
 * - mouseX/mouseY 来自 uiStore 全局鼠标位置
 * @param {DOMHighResTimeStamp} timestamp
 */
function animate(timestamp) {
  if (!startTime) startTime = timestamp
  // 时间流速 0.00025：影响背景流动的速度
  const elapsed = (timestamp - startTime) * 0.00025
  const ctx = canvasRef.value?.getContext('2d')
  if (!ctx) return
  renderNoiseFrame(
    ctx, simplex,
    ctx.canvas.width, ctx.canvas.height,
    elapsed,
    uiStore.mousePosition.x, uiStore.mousePosition.y,
  )
  animId = requestAnimationFrame(animate)
}

onMounted(() => {
  simplex = createNoise3D()
  resize()
  animId = requestAnimationFrame(animate)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
  window.removeEventListener('resize', resize)
})
</script>

<template>
  <!--
    Canvas 层：全屏覆盖、置于最底层、不响应鼠标事件
    z-index: -1 确保在最底层
  -->
  <canvas ref="canvasRef" class="noise-canvas" />
</template>

<style scoped>
/* 全屏固定定位，位于所有内容之下 */
.noise-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: -1;
  pointer-events: none;
}
</style>

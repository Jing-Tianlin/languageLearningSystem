/**
 * Image Trail — 鼠标跟随图片轨迹效果 (优化版)
 *
 * 核心机制：
 * - 鼠标移动时，5-8 个可爱的 SVG 图标以拖尾形式跟随
 * - 图片基准 48×48px，跟随距离逐渐缩小至 50%（24px）
 * - 透明度从 0.85 平滑降至 0.15
 * - 轨迹持续约 600ms 后自然消失
 * - 间距阈值 30px 避免过于密集
 * - 使用 CSS opacity 缓存优化
 */

import { ref } from 'vue'

export function useImageTrail() {
  const trailPoints = ref([])
  // 最多 8 个拖尾点
  const maxPoints = 8
  // 存活 ~600ms
  const maxAge = 600
  // 间距阈值 30px — 平衡流畅度与性能
  const minSpacing = 30

  function addPoint(x, y) {
    const points = trailPoints.value
    if (points.length > 0) {
      const last = points[0]
      const dist = Math.hypot(x - last.x, y - last.y)
      if (dist < minSpacing) return
    }
    points.unshift({ x, y, age: 0 })
    if (points.length > maxPoints) points.length = maxPoints
  }

  function updateTrail(dt) {
    // 限制 dt，避免失焦回来大跳帧
    const clamped = Math.min(dt, 50)
    trailPoints.value.forEach((p) => (p.age += clamped))
    trailPoints.value = trailPoints.value.filter((p) => p.age < maxAge)
  }

  function renderTrail(ctx, images) {
    const points = trailPoints.value
    if (points.length === 0 || images.length === 0) return

    ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)

    for (let i = 0; i < points.length; i++) {
      const p = points[i]
      const progress = Math.min(1, p.age / maxAge)

      // ease-out cubic 衰减，更自然的"消散"感
      const eased = 1 - Math.pow(1 - progress, 3)

      // 透明度 0.85 → 0.15
      const alpha = 0.85 - eased * 0.7

      // 尺寸 48px → 24px
      const scale = 1.0 - eased * 0.5

      const img = images[i % images.length]
      if (!img || !img.complete) continue

      const baseSize = 48
      const size = baseSize * scale

      // 越远偏移越大，模拟"甩尾"感
      const offsetX = eased * 4 * (i % 2 === 0 ? 1 : -1)
      const offsetY = eased * 4 * (i % 3 === 0 ? 1 : -1)

      ctx.globalAlpha = alpha
      ctx.drawImage(
        img,
        p.x - size / 2 + offsetX,
        p.y - size / 2 + offsetY,
        size,
        size,
      )
    }
    ctx.globalAlpha = 1
  }

  return { trailPoints, addPoint, updateTrail, renderTrail }
}

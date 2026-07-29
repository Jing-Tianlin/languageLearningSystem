/**
 * SimplexNoise 动态渐变背景渲染模块
 *
 * 核心机制：
 * - 使用 simplex-noise 库生成 3D Simplex Noise
 * - 将噪声值 [-1, 1] 映射为冷色系渐变：冰蓝 → 灰蓝 → 银灰
 * - 鼠标位置影响噪声采样偏移：颜色偏移 ±10%色值 + 纹理密度变化 ±20%
 * - requestAnimationFrame 驱动，目标 60fps 无卡顿
 */

// === 渐变色板配置 ===
// 冷色系：冰蓝 → 蓝灰 → 银灰白，按 position 划分区间
const COLOR_STOPS = [
  { pos: 0, r: 180, g: 200, b: 220 }, // 冰蓝 #b4c8dc
  { pos: 0.25, r: 200, g: 210, b: 225 }, // 浅蓝灰 #c8d2e1
  { pos: 0.5, r: 215, g: 220, b: 230 }, // 淡蓝灰 #d7dce6
  { pos: 0.75, r: 225, g: 228, b: 235 }, // 银灰 #e1e4eb
  { pos: 1, r: 235, g: 238, b: 242 }, // 浅银白 #ebeef2
]

/**
 * 在两个 RGB 颜色之间线性插值
 * @param {Object} c1 - 起始颜色 {r,g,b}
 * @param {Object} c2 - 结束颜色 {r,g,b}
 * @param {number} t - 插值因子 0~1
 * @returns {Object} 插值结果 {r,g,b}
 */
function lerpColor(c1, c2, t) {
  return {
    r: Math.round(c1.r + (c2.r - c1.r) * t),
    g: Math.round(c1.g + (c2.g - c1.g) * t),
    b: Math.round(c1.b + (c2.b - c1.b) * t),
  }
}

/**
 * 根据位置 t 在 COLOR_STOPS 之间插值获取颜色
 * @param {number} t - 0~1 之间的位置
 * @returns {Object} RGB 颜色
 */
function getColorAt(t) {
  t = Math.max(0, Math.min(1, t))
  let i = 0
  while (i < COLOR_STOPS.length - 2 && t > COLOR_STOPS[i + 1].pos) i++
  const segLen = COLOR_STOPS[i + 1].pos - COLOR_STOPS[i].pos
  const localT = segLen > 0 ? (t - COLOR_STOPS[i].pos) / segLen : 0
  return lerpColor(COLOR_STOPS[i], COLOR_STOPS[i + 1], localT)
}

/**
 * 渲染一帧 Simplex Noise 背景
 *
 * @param {CanvasRenderingContext2D} ctx - Canvas 2D 上下文
 * @param {Object} simplex - simplex-noise 实例 (createNoise3D)
 * @param {number} width - 画布宽度
 * @param {number} height - 画布高度
 * @param {number} time - 累计时间值，驱动动画流动
 * @param {number} mouseX - 鼠标 X 坐标 (用于偏移采样)
 * @param {number} mouseY - 鼠标 Y 坐标
 *
 * 鼠标交互机制：
 * - mouseInfluence 控制鼠标对纹理密度的最大影响 (0~0.2)
 * - 鼠标移动时，噪声采样的 nx/ny 偏移量变化，导致纹理密度 ±20%
 * - 同时颜色映射 t 也会受鼠标轻微影响，产生 ±10% 色值偏移
 */
export function renderNoiseFrame(ctx, simplex, width, height, time, mouseX, mouseY) {
  const imageData = ctx.createImageData(width, height)
  const data = imageData.data

  // 基础噪声缩放系数 — 控制纹理颗粒度
  const baseNoiseScale = 0.003
  // 鼠标对纹理密度的影响强度 (0.08 ~ 0.16 即 ±20%)
  const mouseInfluence = 0.12
  // 将鼠标坐标归一化后作为偏移量
  const mx = (mouseX / Math.max(1, width)) * mouseInfluence
  const my = (mouseY / Math.max(1, height)) * mouseInfluence
  // 鼠标对色彩的影响 (±10% 色值偏移)
  const colorShift = ((mouseX / Math.max(1, width)) - 0.5) * 0.1

  // step=3 表示每 3 像素采样一次噪声 (性能权衡：越小越精细但越慢)
  const step = 3

  for (let y = 0; y < height; y += step) {
    for (let x = 0; x < width; x += step) {
      // 噪声采样坐标：基础位置 + 鼠标偏移 + 时间维度
      const nx = x * baseNoiseScale + mx
      const ny = y * baseNoiseScale + my
      const nz = time
      const noiseVal = simplex(nx, ny, nz) // simplex-noise v4 返回的是可调用函数

      // 映射噪声到颜色位置 [0, 1]，叠加鼠标色彩偏移
      let t = (noiseVal + 1) / 2
      t = Math.max(0, Math.min(1, t + colorShift))
      const color = getColorAt(t)

      // 填充 step×step 像素块 (减少 write 次数)
      for (let dy = 0; dy < step && y + dy < height; dy++) {
        for (let dx = 0; dx < step && x + dx < width; dx++) {
          const idx = ((y + dy) * width + (x + dx)) * 4
          data[idx] = color.r
          data[idx + 1] = color.g
          data[idx + 2] = color.b
          data[idx + 3] = 255 // alpha 不透明
        }
      }
    }
  }

  ctx.putImageData(imageData, 0, 0)
}

/**
 * useHesitationTracker.js — 犹豫度追踪 (提议2)
 *
 * 监听用户在输入框中的键盘动态：
 * - 记录两次按键之间的间隔
 * - 如果间隔 > 2000ms，标记为"认知负荷过高"
 * - 返回所有按键间隔的统计信息
 *
 * 用法：
 *   const { attach, getStats, reset } = useHesitationTracker()
 *   <input ref="inputRef" @keydown="trackKey" />
 */
import { ref } from 'vue'

export function useHesitationTracker() {
  // 按键时间戳记录
  const keyTimestamps = ref([])
  // 是否有过犹豫（>2000ms间隔）
  const hasHesitation = ref(false)
  // 最大停顿间隔
  const maxPause = ref(0)
  // 总停顿次数
  const pauseCount = ref(0)
  // 最后一次按键时间
  let lastKeyTime = 0

  /**
   * 跟踪按键
   * 在 input 的 @keydown 或 @input 中调用
   */
  function trackKey() {
    const now = performance.now()
    if (lastKeyTime > 0) {
      const gap = now - lastKeyTime
      keyTimestamps.value.push(gap)
      if (gap > 2000) {
        hasHesitation.value = true
        pauseCount.value++
      }
      if (gap > maxPause.value) maxPause.value = Math.round(gap)
    }
    lastKeyTime = now
  }

  /**
   * 获取最终统计
   */
  function getStats() {
    const gaps = keyTimestamps.value
    if (gaps.length === 0) return { avgMs: 0, maxMs: 0, count: 0, hasHesitation: false }
    const sum = gaps.reduce((a, b) => a + b, 0)
    return {
      avgMs: Math.round(sum / gaps.length),
      maxMs: maxPause.value,
      hesitationCount: pauseCount.value,
      hasHesitation: hasHesitation.value,
    }
  }

  /** 重置状态 */
  function reset() {
    keyTimestamps.value = []
    hasHesitation.value = false
    maxPause.value = 0
    pauseCount.value = 0
    lastKeyTime = 0
  }

  return { trackKey, getStats, reset }
}

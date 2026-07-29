/**
 * Letter Swap — 文字悬停字母随机交换动画
 *
 * 核心机制：
 * - 鼠标 hover 时，每个字母以随机延迟逐个变换为随机大写字母
 * - 经过 3-4 轮随机交换后恢复为原始字母
 * - 动画持续约 0.2 秒（每个字符的 swap 周期）
 * - 字母范围限定为 A-Z（大写字母）
 */

import { ref } from 'vue'

export function useLetterSwap(text, swapChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') {
  // 原始字符数组
  const chars = text.split('')
  // 当前显示的字符数组 (响应式)
  const displayChars = ref([...chars])
  // 是否正在交换中 (防止重复触发)
  const isSwapping = ref(false)
  // 存储所有定时器以便清除
  let timers = []

  /**
   * 每个字符的延迟样式 (用于 CSS transition-delay stagger 效果)
   * @param {number} index - 字符索引
   * @returns {Object} 样式对象
   */
  const letterStyle = (index) => ({
    transitionDelay: `${index * 30}ms`,
  })

  /**
   * 启动字母交换动画
   * - 每个非空格字符独立进行 4 轮随机替换
   * - 每轮间隔约 60ms + 随机抖动，总时长约 0.2s
   * - 所有字符恢复后 isSwapping 置为 false
   */
  function startSwap() {
    if (isSwapping.value) return // 防止连续触发
    isSwapping.value = true
    timers = []

    chars.forEach((original, i) => {
      if (original === ' ') return // 空格不变
      let swaps = 0
      const maxSwaps = 4 // 4 轮交换，总时长 ≈ 4×50ms ≈ 200ms

      const interval = setInterval(() => {
        if (swaps >= maxSwaps) {
          // 恢复原始字符
          clearInterval(interval)
          displayChars.value[i] = original
          // 检查是否所有字符都已恢复
          if (i === chars.length - 1 || chars.filter((c) => c !== ' ').every((_, j) => displayChars.value[j] === chars[j])) {
            isSwapping.value = false
          }
          return
        }
        // 随机选取一个交换字母
        const randChar = swapChars[Math.floor(Math.random() * swapChars.length)]
        displayChars.value[i] = randChar
        swaps++
      }, 50 + Math.random() * 30) // 50~80ms 间隔
      timers.push(interval)
    })
  }

  /**
   * 重置交换动画
   * - 清除所有定时器
   * - 恢复原始字符
   */
  function resetSwap() {
    timers.forEach(clearInterval)
    displayChars.value = [...chars]
    isSwapping.value = false
  }

  return { displayChars, isSwapping, letterStyle, startSwap, resetSwap }
}

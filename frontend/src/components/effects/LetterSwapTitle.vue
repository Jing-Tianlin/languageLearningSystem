<script setup>
/**
 * LetterSwapTitle.vue — 文字悬停字母交换动画
 *
 * 规范要求：
 * - hover 时字母随机交换位置
 * - 动画持续 0.2s (ease-in-out)，恢复原状
 * - 文字颜色默认墨色，适合 Playful 主题
 * - 字体 Arial Regular 18px
 *
 * Props:
 *   text  - 要显示的文字
 *   tag   - 渲染的 HTML 标签 (默认 h2)
 *   color - 文字颜色 (默认 #2a2438)
 *   fontSize - 字体大小 (默认 18px)
 */
import { useLetterSwap } from '@/composables/useLetterSwap'

const props = defineProps({
  text: { type: String, required: true },
  tag: { type: String, default: 'h2' },
  color: { type: String, default: '#2a2438' },
  fontSize: { type: String, default: '18px' },
  fontWeight: { type: [String, Number], default: 800 },
  letterSpacing: { type: String, default: 'normal' },
})

const { displayChars, startSwap, resetSwap } = useLetterSwap(props.text)
</script>

<template>
  <component
    :is="tag"
    class="letter-swap-title"
    :style="{
      '--swap-color': color,
      '--swap-font-size': fontSize,
      '--swap-font-weight': fontWeight,
      '--swap-letter-spacing': letterSpacing,
    }"
    @mouseenter="startSwap"
    @mouseleave="resetSwap"
  >
    <span
      v-for="(char, i) in displayChars"
      :key="i"
      class="letter-swap-char"
      :class="{ 'is-space': char === ' ' }"
      :style="{ transitionDelay: `${i * 20}ms` }"
    >
      <!-- 空格用 non-breaking space 保留宽度 -->
      {{ char === ' ' ? ' ' : char }}
    </span>
  </component>
</template>

<style scoped>
/* 基础容器 */
.letter-swap-title {
  cursor: default;
  user-select: none;
  font-family: 'Arial Rounded MT Bold', ui-rounded, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif;
  font-weight: var(--swap-font-weight, 700);
  font-size: var(--swap-font-size, 18px);
  color: var(--swap-color, #2a2438);
  letter-spacing: var(--swap-letter-spacing, normal);
}

/* 每个可交换的字符，inline-block 支持 transform */
.letter-swap-char {
  display: inline-block;
  transition: color 0.2s ease-in-out, text-shadow 0.2s ease-in-out;
}

/* hover 时字符微微发光，增强交互反馈 */
.letter-swap-title:hover .letter-swap-char {
  text-shadow: 2px 2px 0 rgba(255, 107, 107, 0.25);
}

/* 空格保留宽度 */
.letter-swap-char.is-space {
  width: 0.35em;
}
</style>

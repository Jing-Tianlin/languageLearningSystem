<script setup>
/**
 * LearningRoadmap.vue — 语言学习路径组件
 *
 * 展示语言学习的必要步骤 (step-by-step 路线图)：
 *   字母/发音 → 基础词汇 → 语法入门 → 简单阅读 → 听力训练 → 口语表达 → 沉浸练习
 * 每步有图标、标题、简短描述，点击可跳转对应课程页面
 */
import { ref, computed } from 'vue'
import CircleCard from '@/components/cards/CircleCard.vue'

const props = defineProps({
  langCode: { type: String, default: 'en' },
})

const emit = defineEmits(['select'])

/**
 * 语言学习的 7 个必经步骤
 * step 数字越大越靠后
 */
const steps = [
  {
    step: 1,
    title: '字母与发音',
    subtitle: '掌握书写系统',
    icon: 'vocab',
    desc: '学习字母表、音标、发音规则。这是语言学习的基础，就像建房的砖块。',
    color: '#7c9db5',
  },
  {
    step: 2,
    title: '基础词汇',
    subtitle: '积累核心 500 词',
    icon: 'book',
    desc: '掌握日常高频词汇。建议先学问候语、数字、颜色、食物等实用主题。',
    color: '#5a7d96',
  },
  {
    step: 3,
    title: '语法入门',
    subtitle: '理解句子结构',
    icon: 'grammar',
    desc: '学习基本句型（主谓宾）、时态变化、词性搭配。语法是沟通的骨架。',
    color: '#8faec4',
  },
  {
    step: 4,
    title: '简单阅读',
    subtitle: '从短文学起',
    icon: 'book',
    desc: '阅读简易文章、儿童故事、新闻标题。在语境中巩固词汇和语法。',
    color: '#6b8fa8',
  },
  {
    step: 5,
    title: '听力训练',
    subtitle: '磨耳朵阶段',
    icon: 'ai',
    desc: '听播客、看视频、跟读对话。让耳朵熟悉语音语调，培养语感。',
    color: '#4a7d96',
  },
  {
    step: 6,
    title: '口语表达',
    subtitle: '开口说出来',
    icon: 'speak',
    desc: '模仿发音、朗读句子、与外教对话。语言最终是为了交流。',
    color: '#3d6d85',
  },
  {
    step: 7,
    title: '沉浸练习',
    subtitle: '每日巩固提升',
    icon: 'star',
    desc: '每日复习 + 做练习 + 看原声内容。让语言成为生活的一部分。',
    color: '#5a7d96',
  },
]

const activeIndex = ref(0)
const currentStep = computed(() => steps[activeIndex.value])

function select(index) {
  activeIndex.value = index
  emit('select', { step: steps[index], index })
}
</script>

<template>
  <div class="roadmap-section">
    <!-- 左侧：当前选中步骤的大卡片说明 -->
    <div class="roadmap-card-area">
      <transition name="fade" mode="out-in">
        <div v-if="currentStep" :key="currentStep.step" class="roadmap-detail-card">
          <div class="step-number">Step {{ currentStep.step }}</div>
          <span class="step-big-icon icon-svg" :class="currentStep.icon"></span>
          <h3 class="step-detail-title">{{ currentStep.title }}</h3>
          <p class="step-detail-sub">{{ currentStep.subtitle }}</p>
          <p class="step-detail-desc">{{ currentStep.desc }}</p>
          <div class="step-tags">
            <span class="step-tag" v-if="currentStep.step <= 2">入门阶段</span>
            <span class="step-tag" v-else-if="currentStep.step <= 4">进阶阶段</span>
            <span class="step-tag" v-else>精通阶段</span>
            <span class="step-tag">{{ currentStep.step }}/7</span>
          </div>
        </div>
      </transition>
    </div>

    <!-- 右侧：垂直步骤时间轴 -->
    <div class="roadmap-timeline">
      <div
        v-for="(s, i) in steps"
        :key="s.step"
        class="roadmap-item"
        :class="{ active: activeIndex === i, passed: i < activeIndex }"
        @click="select(i)"
      >
        <!-- 连接线 -->
        <div class="rm-dot-wrap">
          <div
            class="rm-line"
            :class="{ 'line-first': i === 0, 'line-last': i === steps.length - 1 }"
          />
          <div class="rm-dot" :style="{ background: activeIndex === i ? s.color : '#ccc' }">
            <span class="rm-dot-icon icon-svg" :class="s.icon"></span>
          </div>
        </div>
        <!-- 步骤信息 -->
        <div class="rm-content">
          <span class="rm-title" :style="{ color: activeIndex === i ? s.color : '#666' }">
            {{ s.title }}
          </span>
          <span class="rm-subtitle">{{ s.subtitle }}</span>
        </div>
        <!-- 完成勾 -->
        <span v-if="i < activeIndex" class="rm-check">✓</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 整体布局 ===== */
.roadmap-section {
  display: flex;
  gap: 48px;
  align-items: center;
  padding: 24px 0;
  max-width: 1000px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .roadmap-section {
    flex-direction: column;
    gap: 24px;
  }
}

/* ===== 左侧详情卡片 ===== */
.roadmap-card-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 320px;
}

.roadmap-detail-card {
  width: 100%;
  max-width: 380px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 36px 32px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
}

.step-number {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 12px;
}

.step-big-icon {
  font-size: 56px;
  display: block;
  margin-bottom: 16px;
}

.step-detail-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 6px;
}

.step-detail-sub {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.step-detail-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  line-height: 1.7;
  margin-bottom: 20px;
}

.step-tags {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.step-tag {
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 12px;
  background: rgba(124, 157, 181, 0.12);
  color: var(--color-primary-dark);
  font-weight: 600;
}

/* ===== fade 过渡 ===== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.35s ease, transform 0.35s ease;
}
.fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.fade-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

/* ===== 右侧时间轴 ===== */
.roadmap-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex-shrink: 0;
  min-width: 360px;
}

.roadmap-item {
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  padding: 8px 4px;
  position: relative;
  transition: all 0.3s ease;
  border-radius: 12px;
}

.roadmap-item:hover {
  background: rgba(0, 0, 0, 0.02);
}

.roadmap-item.active {
  background: rgba(124, 157, 181, 0.06);
}

/* 圆点 + 线 */
.rm-dot-wrap {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rm-dot {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.35s ease;
  z-index: 1;
  position: relative;
}

.rm-dot-icon {
  font-size: 18px;
  position: absolute;
  opacity: 0;
  transition: opacity 0.3s;
}

.roadmap-item.active .rm-dot-icon,
.roadmap-item.passed .rm-dot-icon {
  opacity: 1;
}

/* 垂直线 */
.rm-line {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #ddd;
  transform: translateX(-50%);
}

.rm-line.line-first {
  top: 50%;
}

.rm-line.line-last {
  bottom: 50%;
}

/* 步骤信息 */
.rm-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.rm-title {
  font-size: 15px;
  font-weight: 600;
  transition: color 0.3s;
}

.rm-subtitle {
  font-size: 12px;
  color: #aaa;
}

/* 完成勾 */
.rm-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #27ae60;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
</style>

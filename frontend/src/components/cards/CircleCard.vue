<script setup>
/**
 * CircleCard.vue — 圆形项目展示卡片 (Featured Projects)
 *
 * 规范要求：
 * - 圆形卡片，直径 180px
 * - 包含项目图标/插画、下方标注项目名称
 * - 点击切换时：卡片旋转 180° + 淡入淡出 (0.5s duration)
 * - hover 缩放 + 阴影
 *
 * Props:
 *   imageUrl  - 项目缩略图 URL
 *   title     - 项目名称 (如 "airbag studio")
 *   subtitle  - 副标题
 *   to        - router-link 跳转路径
 *   year      - 年份标签 (用于时间轴)
 */
defineProps({
  imageUrl: { type: String, default: '' },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  to: { type: String, default: '' },
  year: { type: [String, Number], default: '' },
})
</script>

<template>
  <!-- 如果有 to 路径则渲染为 router-link -->
  <router-link :to="to" class="circle-card-link" v-if="to">
    <div class="circle-card">
      <div class="circle-card-inner">
        <img v-if="imageUrl" :src="imageUrl" class="circle-card-img" alt="" />
        <slot name="icon" />
        <span class="circle-card-title">{{ title }}</span>
        <span v-if="subtitle" class="circle-card-subtitle">{{ subtitle }}</span>
        <span v-if="year" class="circle-card-year">{{ year }}</span>
      </div>
    </div>
  </router-link>

  <!-- 否则渲染为普通 div -->
  <div class="circle-card" v-else @click="$emit('select')">
    <div class="circle-card-inner">
      <img v-if="imageUrl" :src="imageUrl" class="circle-card-img" alt="" />
      <slot name="icon" />
      <span class="circle-card-title">{{ title }}</span>
      <span v-if="subtitle" class="circle-card-subtitle">{{ subtitle }}</span>
      <span v-if="year" class="circle-card-year">{{ year }}</span>
    </div>
  </div>
</template>

<style scoped>
/* === 外层链接重置 === */
.circle-card-link {
  text-decoration: none;
  color: inherit;
}

/* === 圆形卡片 — 直径 180px === */
.circle-card {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1.5px solid rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.5s cubic-bezier(0.25, 0.46, 0.45, 0.94), box-shadow 0.5s ease, opacity 0.5s ease;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
  box-shadow: 0 4px 16px rgba(124, 92, 191, 0.08);
}

/* hover 放大 + 阴影 */
.circle-card:hover {
  transform: scale(1.1) translateY(-8px);
  box-shadow: 0 18px 44px rgba(124, 92, 191, 0.2);
  border-color: rgba(124, 92, 191, 0.3);
}

/* === 内容居中布局 === */
.circle-card-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px;
  text-align: center;
}

/* === 图片/图标 === */
.circle-card-img {
  width: 60px;
  height: 60px;
  object-fit: contain;
  border-radius: 14px;
}

/* === 标题 — 项目名称 === */
.circle-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1028;
  font-family: Arial, 'Helvetica Neue', sans-serif;
}

/* === 副标题 === */
.circle-card-subtitle {
  font-size: 13px;
  color: rgba(26, 16, 40, 0.5);
  font-weight: 400;
}

/* === 年份标签 === */
.circle-card-year {
  font-size: 11px;
  background: rgba(124, 92, 191, 0.1);
  color: #7c5cbf;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
}
</style>

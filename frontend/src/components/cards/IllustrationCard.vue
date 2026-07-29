<script setup>
/**
 * IllustrationCard.vue — 插画设计展示卡片
 *
 * 规范要求：
 * - 圆角矩形，300×200px (宽×高)，圆角 8px
 * - 背景粉紫渐变 (#f8e7f3 → #d4a5ff)
 * - 悬停时弹出项目详情
 * - 标题 Helvetica Bold 24px #333，正文 Helvetica Regular 16px #666
 *
 * Props:
 *   imageUrl   - 插画图片 URL
 *   title      - 插画标题
 *   author     - 作者
 *   tags       - 标签数组
 *   detail     - 悬停时显示的详情
 */
defineProps({
  imageUrl: { type: String, default: '' },
  title: { type: String, default: '' },
  author: { type: String, default: '' },
  tags: { type: Array, default: () => [] },
  detail: { type: String, default: '' },
})
</script>

<template>
  <div class="illustration-card">
    <!-- 图片区域 -->
    <div class="illustration-img-wrap">
      <img v-if="imageUrl" :src="imageUrl" :alt="title" class="illustration-img" />
      <!-- 无图片时的占位符 -->
      <div v-else class="illustration-placeholder">
        <span></span>
      </div>
    </div>

    <!-- 信息区域 — hover 时通过 :hover + .card-hover-detail 显示详情 -->
    <div class="illustration-info">
      <h3 class="illustration-title">{{ title }}</h3>
      <p v-if="author" class="illustration-author">{{ author }}</p>
      <div v-if="tags.length" class="illustration-tags">
        <span v-for="tag in tags" :key="tag" class="illustration-tag">{{ tag }}</span>
      </div>
    </div>

    <!-- 悬停弹出详情层 -->
    <div v-if="detail" class="card-hover-detail">
      <p>{{ detail }}</p>
    </div>
  </div>
</template>

<style scoped>
/* === 卡片基础 === */
.illustration-card {
  break-inside: avoid;
  margin-bottom: 1.5rem;
  border-radius: 8px;
  overflow: hidden;
  /* 粉紫渐变背景 */
  background: linear-gradient(135deg, #f8e7f3, #d4a5ff);
  border: 1px solid rgba(0, 0, 0, 0.05);
  transition: transform 0.45s cubic-bezier(0.25, 0.46, 0.45, 0.94), box-shadow 0.4s ease;
  cursor: pointer;
  position: relative;
}

/* hover 放大 */
.illustration-card:hover {
  transform: scale(1.025) translateY(-4px);
  box-shadow: 0 16px 48px rgba(180, 130, 200, 0.2);
}

/* 固定宽度 300px */
@media (min-width: 640px) {
  .illustration-card {
    width: 300px;
  }
}

/* === 图片 === */
.illustration-img-wrap {
  width: 100%;
}
.illustration-img {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
  transition: filter 0.35s ease;
}
.illustration-card:hover .illustration-img {
  filter: brightness(1.05) saturate(1.1);
}

/* 占位 */
.illustration-placeholder {
  width: 100%;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8e7f3, #d4a5ff);
  font-size: 48px;
}

/* === 信息 === */
.illustration-info {
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.7);
}
/* 标题 — Helvetica Bold 24px #333 */
.illustration-title {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin-bottom: 8px;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}
/* 作者 — #666 16px */
.illustration-author {
  font-size: 16px;
  color: #666;
  margin-bottom: 10px;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
}

/* 标签 */
.illustration-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.illustration-tag {
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 10px;
  background: rgba(106, 68, 117, 0.1);
  color: #6a4475;
  font-weight: 500;
}

/* === 悬停弹出详情层 === */
.card-hover-detail {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.95);
  padding: 16px 18px;
  transform: translateY(100%);
  transition: transform 0.35s ease-in-out;
  font-size: 14px;
  color: #555;
  line-height: 1.5;
  border-top: 1px solid #f0e0f5;
}
.illustration-card:hover .card-hover-detail {
  transform: translateY(0);
}
</style>

<script setup>
/**
 * Pagination.vue — 增强分页组件
 *
 * 新增能力：
 * - 页码过多时自动折叠为 1 ... 5 6 7 ... 20
 * - 显示"共 X 条"记录数
 * - 支持快速跳转首页/末页
 * - 支持每页条数切换
 */
import { ref, watch, computed } from 'vue'

const props = defineProps({
  currentPage: { type: Number, default: 1 },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 12 },
})

const emit = defineEmits(['page-change', 'size-change'])

const totalPages = ref(1)
const showSizeSelector = ref(false)

watch(
  () => [props.total, props.pageSize],
  () => {
    totalPages.value = Math.max(1, Math.ceil(props.total / props.pageSize))
  },
  { immediate: true },
)

// 智能页码生成：超过 7 页时折叠
const visiblePages = computed(() => {
  const total = totalPages.value
  const current = props.currentPage
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }

  const pages = []
  pages.push(1)

  if (current > 3) pages.push('...')

  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  if (current < total - 2) pages.push('...')

  pages.push(total)
  return pages
})

function go(page) {
  if (page === '...') return
  if (page >= 1 && page <= totalPages.value) {
    emit('page-change', page)
  }
}

function changeSize(e) {
  emit('size-change', Number(e.target.value))
}
</script>

<template>
  <div class="pagination-wrap" v-if="totalPages >= 1">
    <!-- 记录数摘要 -->
    <div class="pagination-summary">
      共 <strong>{{ total }}</strong> 条，
      第 <strong>{{ currentPage }}</strong> / <strong>{{ totalPages }}</strong> 页
    </div>

    <!-- 页码按钮组 -->
    <div class="pagination">
      <!-- 首页 -->
      <button :disabled="currentPage <= 1" @click="go(1)" title="首页">«</button>
      <!-- 上一页 -->
      <button :disabled="currentPage <= 1" @click="go(currentPage - 1)">‹</button>

      <template v-for="p in visiblePages" :key="p">
        <span v-if="p === '...'" class="ellipsis">…</span>
        <button
          v-else
          :class="{ active: p === currentPage }"
          @click="go(p)"
        >{{ p }}</button>
      </template>

      <!-- 下一页 -->
      <button :disabled="currentPage >= totalPages" @click="go(currentPage + 1)">›</button>
      <!-- 末页 -->
      <button :disabled="currentPage >= totalPages" @click="go(totalPages)" title="末页">»</button>
    </div>

    <!-- 每页条数切换 (可选) -->
    <div class="pagination-size" v-if="showSizeSelector">
      <label>每页</label>
      <select :value="pageSize" @change="changeSize">
        <option :value="6">6 条</option>
        <option :value="12">12 条</option>
        <option :value="24">24 条</option>
        <option :value="48">48 条</option>
      </select>
    </div>
  </div>
</template>

<style scoped>
.pagination-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  margin-top: 32px;
  padding-bottom: 20px;
}

.pagination-summary {
  font-size: 13px;
  color: var(--color-text-muted);
}

.pagination-summary strong {
  color: var(--color-text);
  font-weight: 600;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.pagination button {
  min-width: 38px;
  height: 38px;
  padding: 0 6px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(8px);
  color: var(--color-text);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.25s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination button:hover:not(:disabled) {
  background: rgba(123, 155, 181, 0.12);
  border-color: rgba(123, 155, 181, 0.3);
  transform: translateY(-1px);
}

.pagination button.active {
  background: rgba(123, 155, 181, 0.2);
  border-color: rgba(123, 155, 181, 0.45);
  color: var(--color-primary-dark);
  font-weight: 700;
}

.pagination button:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}

.ellipsis {
  width: 38px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
  user-select: none;
}

.pagination-size {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.pagination-size select {
  padding: 5px 10px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.5);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
}
</style>

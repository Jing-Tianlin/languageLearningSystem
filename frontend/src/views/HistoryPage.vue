<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const authStore = useAuthStore()
const BASE = API_BASE_URL
const tab = ref('chat') // chat | writing | reading

const chats = ref([])
const writings = ref([])
const readings = ref([])
const loading = ref(false)

onMounted(async () => {
  if (!authStore.isLoggedIn || !authStore.user) return
  loading.value = true
  const uid = authStore.user.id
  try {
    const [cRes, wRes, rRes] = await Promise.all([
      fetchJson(`${BASE}/history/chat?userId=${uid}&limit=100`),
      fetchJson(`${BASE}/history/writing?userId=${uid}&limit=20`),
      fetchJson(`${BASE}/history/reading?userId=${uid}&limit=20`),
    ])
    chats.value = cRes.data || []
    writings.value = wRes.data || []
    readings.value = rRes.data || []
  } catch (e) {}
  finally { loading.value = false }
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="学习记录" tag="h1" />
      <p class="page-sub">你的学习历程</p>
    </div>

    <div class="tab-bar">
      <button class="btn" :class="tab === 'chat' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="tab = 'chat'">AI 对话</button>
      <button class="btn" :class="tab === 'writing' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="tab = 'writing'">写作记录</button>
      <button class="btn" :class="tab === 'reading' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="tab = 'reading'">阅读记录</button>
    </div>

    <LoadingSpinner v-if="loading" />

    <!-- AI 对话 -->
    <div v-else-if="tab === 'chat'">
      <div v-if="chats.length > 0" class="chat-list">
        <div v-for="m in chats" :key="m.id" class="chat-item" :class="m.role">
          <span class="chat-role">{{ m.role === 'user' ? '你' : 'AI' }}</span>
          <span class="chat-content">{{ m.content }}</span>
          <span class="chat-time">{{ (m.created_at || '').substring(0, 16) }}</span>
        </div>
      </div>
      <EmptyState v-else icon="chat" title="暂无对话记录" description="与 AI 助手对话后，历史会显示在这里" />
    </div>

    <!-- 写作 -->
    <div v-else-if="tab === 'writing'">
      <div v-if="writings.length > 0" class="record-list">
        <div v-for="w in writings" :key="w.id" class="record-item">
          <div class="ri-top">
            <span class="ri-topic">{{ w.topic || '无主题' }}</span>
            <span class="ri-type">{{ w.type }}</span>
          </div>
          <div class="ri-meta">
            <span>{{ w.level ? 'L' + w.level : '' }}</span>
            <span>{{ (w.submitted_at || '').substring(0, 16) }}</span>
          </div>
        </div>
      </div>
      <EmptyState v-else icon="pen" title="暂无写作记录" description="完成写作训练后，记录会显示在这里" />
    </div>

    <!-- 阅读 -->
    <div v-else-if="tab === 'reading'">
      <div v-if="readings.length > 0" class="record-list">
        <div v-for="r in readings" :key="r.id" class="record-item">
          <div class="ri-top">
            <span class="ri-topic">{{ r.article_title }}</span>
            <span class="ri-score">{{ r.quiz_score }}/{{ r.quiz_total }}</span>
          </div>
          <div class="ri-meta">
            <span>{{ r.article_level }}</span>
            <span>{{ (r.completed_at || '').substring(0, 16) }}</span>
          </div>
        </div>
      </div>
      <EmptyState v-else icon="book" title="暂无阅读记录" description="完成阅读训练后，记录会显示在这里" />
    </div>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 680px; margin: 0 auto; padding-bottom: 60px; }
.page-header { text-align: center; padding: 20px 0 8px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); }
.page-sub { font-size: 14px; color: var(--color-text-muted); }
.loading { text-align: center; padding: 40px; color: #a49ec0; }
.empty { text-align: center; padding: 40px; color: #b9b3d0; font-size: 14px; }

.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 16px 0; }
.tab-bar button {
  padding: 8px 20px; border-radius: 20px; border: 1.5px solid #ddd8e9;
  background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; color: #5f5a70; cursor: pointer;
  transition: all 0.2s;
}
.tab-bar button.active { border-color: #4d96ff; color: #4d96ff; background: rgba(77,150,255,0.06); }

/* 对话 */
.chat-list { display: flex; flex-direction: column; gap: 10px; }
.chat-item {
  padding: 10px 14px; border-radius: 10px; display: flex; gap: 8px; align-items: flex-start;
}
.chat-item.user { background: #eef5ff; }
.chat-item.ai { background: #f6f9ff; }
.chat-role { font-size: 11px; font-weight: 700; color: #4d96ff; white-space: nowrap; min-width: 24px; }
.chat-content { font-size: 14px; color: #3f3a4d; line-height: 1.6; flex: 1; }
.chat-time { font-size: 11px; color: #cdc7de; white-space: nowrap; }

/* 记录 */
.record-list { display: flex; flex-direction: column; gap: 8px; }
.record-item {
  padding: 14px 16px; border-radius: 12px;
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.04);
}
.ri-top { display: flex; justify-content: space-between; margin-bottom: 6px; }
.ri-topic { font-size: 15px; font-weight: 600; color: var(--color-text); }
.ri-type { font-size: 12px; color: #4d96ff; padding: 2px 8px; border-radius: 8px; background: rgba(77,150,255,0.08); }
.ri-score { font-size: 14px; font-weight: 700; color: #4d96ff; }
.ri-meta { display: flex; gap: 12px; font-size: 12px; color: #a49ec0; }
</style>

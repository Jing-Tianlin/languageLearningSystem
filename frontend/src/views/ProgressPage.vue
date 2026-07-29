<script setup>
import { onMounted } from 'vue'
import { useProgressStore } from '@/stores/progress'
import { useAuthStore } from '@/stores/auth'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useLanguageStore } from '@/stores/language'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StudyStatsCard from '@/components/cards/StudyStatsCard.vue'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const progressStore = useProgressStore()
const authStore = useAuthStore()
const vocabularyStore = useVocabularyStore()
const languageStore = useLanguageStore()

onMounted(async () => {
  if (!authStore.isLoggedIn || !authStore.user) return
  await progressStore.fetchProgress({ userId: authStore.user.id, pageSize: 200 })
  await vocabularyStore.fetchVocabularies({ pageSize: 200 })
  await languageStore.fetchLanguages()
  progressStore.computeStats()
})

function wordFor(vocabId) {
  return vocabularyStore.vocabularyList.find((v) => v.id === vocabId)
}

function langFor(code) {
  return languageStore.languages.find((l) => l.code === code)
}
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="学习进度" tag="h1" />
      <p class="page-sub" v-if="authStore.isLoggedIn">追踪你的学习成果</p>
    </div>

    <div v-if="!authStore.isLoggedIn" class="empty-text">
      请先<a href="#" @click.prevent="$router.push('/login')">登录</a>
    </div>

    <LoadingSpinner v-else-if="progressStore.loading" />

    <template v-else>
      <div class="stats-row">
        <StudyStatsCard icon="practice" label="总词汇" :value="progressStore.stats.totalWords" />
        <StudyStatsCard icon="practice" label="已学习" :value="progressStore.stats.studiedWords" />
        <StudyStatsCard icon="star" label="已掌握" :value="progressStore.stats.masteredWords" />
      </div>

      <div class="progress-list" v-if="progressStore.progressRecords.length > 0">
        <div
          v-for="record in progressStore.progressRecords"
          :key="record.id"
          class="progress-item"
        >
          <div class="progress-item-header">
            <span class="progress-word-name">
              {{ wordFor(record.vocabId)?.word || '单词#' + record.vocabId }}
            </span>
            <span class="progress-lang">
              {{ langFor(record.langCode)?.nameCn || record.langCode }}
            </span>
          </div>
          <ProgressBar
            :percent="Math.min(100, (record.familiarity || 0))"
            :label="`掌握度`"
          />
          <div class="progress-detail">
            <span>复习 {{ record.reviewCount || 0 }} 次</span>
            <span v-if="record.lastReviewTime">上次: {{ record.lastReviewTime }}</span>
            <span v-if="record.nextReviewTime">下次: {{ record.nextReviewTime }}</span>
          </div>
        </div>
      </div>

      <p v-else class="empty-text">还没有学习记录，开始学习吧</p>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  text-align: center;
  padding: 24px 0 16px;
}

.page-header :deep(.letter-swap-title) {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 8px;
}

.page-sub {
  font-size: 15px;
  color: var(--color-text-secondary);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 28px;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-item {
  background: var(--color-bg-card);
  backdrop-filter: blur(12px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 18px 22px;
}

.progress-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.progress-word-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.progress-lang {
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-bg-glass);
  padding: 2px 10px;
  border-radius: 8px;
}

.progress-detail {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}

.empty-text {
  text-align: center;
  color: var(--color-text-muted);
  padding: 60px 0;
  font-size: 15px;
}

.empty-text a {
  color: var(--color-primary-dark);
  text-decoration: none;
  font-weight: 500;
}
</style>

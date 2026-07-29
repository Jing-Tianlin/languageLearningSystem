<script setup>
/**
 * FavoritesPage.vue — 我的收藏
 *
 * 自动按当前学习语言展示收藏，顶部语言标签一键切换
 */
import { onMounted, ref, computed, watch } from 'vue'
import { useFavoriteStore } from '@/stores/favorite'
import { useAuthStore } from '@/stores/auth'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useLanguageStore } from '@/stores/language'
import { useProgressStore } from '@/stores/progress'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const favoriteStore = useFavoriteStore()
const authStore = useAuthStore()
const vocabularyStore = useVocabularyStore()
const languageStore = useLanguageStore()
const progressStore = useProgressStore()

const activeLang = ref(authStore.targetLanguage || '')

watch(() => authStore.targetLanguage, (v) => { activeLang.value = v || '' })

const batchMode = ref(false)
const selectedIds = ref(new Set())
const selectAll = ref(false)
const searchText = ref('')
const revealedIds = ref(new Set()) // 点击后显示翻译的收藏ID

function toggleReveal(favId) {
  if (revealedIds.value.has(favId)) {
    revealedIds.value.delete(favId)
  } else {
    revealedIds.value.add(favId)
  }
}

function enterBatchMode() {
  batchMode.value = true
  selectedIds.value.clear()
  selectAll.value = false
}

function exitBatchMode() {
  batchMode.value = false
  selectedIds.value.clear()
  selectAll.value = false
}

// 按语言分组统计
const favByLang = computed(() => {
  const map = {}
  favoriteStore.favorites.forEach(f => {
    if (!map[f.langCode]) map[f.langCode] = []
    map[f.langCode].push(f)
  })
  return map
})

const langTabs = computed(() => {
  return Object.entries(favByLang.value).map(([code, favs]) => ({
    code,
    name: getLangName(code),
    count: favs.length,
  }))
})

// 当前语言的收藏列表
const currentFavs = computed(() => {
  let list = activeLang.value ? (favByLang.value[activeLang.value] || []) : favoriteStore.favorites
  if (searchText.value) {
    const kw = searchText.value.toLowerCase()
    list = list.filter(f => {
      const v = vocabMeta(f.vocabId)
      return (v.word || '').toLowerCase().includes(kw) || (v.definition || '').includes(kw)
    })
  }
  return list
})

function vocabMeta(vocabId) {
  return vocabularyStore.vocabularyList.find(v => v.id === vocabId) || {}
}

function progressMeta(vocabId) {
  return progressStore.progressRecords.find(p => p.vocabId === vocabId) || {}
}

function getLangName(code) {
  const l = languageStore.languages.find(l => l.code === code)
  return l ? l.nameCn : code?.toUpperCase() || ''
}

function removeFav(fav) { favoriteStore.removeFavorite(fav.vocabId) }

function batchRemove() {
  selectedIds.value.forEach(vocabId => favoriteStore.removeFavorite(vocabId))
  exitBatchMode()
}

function toggleAll() {
  if (selectAll.value) {
    currentFavs.value.forEach(f => selectedIds.value.add(f.vocabId))
  } else {
    selectedIds.value.clear()
  }
}

const masteryLabels = { 0: '新词', 1: '学习中', 2: '熟悉', 3: '已掌握' }
const masteryColors = { 0: '#bbb', 1: '#f0975c', 2: '#5a7d96', 3: '#27ae60' }

onMounted(async () => {
  await languageStore.fetchLanguages()
  if (authStore.isLoggedIn && authStore.user) {
    await favoriteStore.fetchFavorites({ userId: authStore.user.id, pageSize: 500 })
    // 加载全部词汇以避免收藏中的单词显示"未知"
    await vocabularyStore.fetchVocabularies({ pageSize: 5000 })
    await progressStore.fetchProgress({ userId: authStore.user.id, pageSize: 500 })
  }
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="我的收藏" tag="h1" />
      <p class="page-sub">
        共收藏 <strong>{{ favoriteStore.favorites.length }}</strong> 个词汇
        <template v-if="langTabs.length">，覆盖 <strong>{{ langTabs.length }}</strong> 门语言</template>
      </p>
    </div>

    <div v-if="!authStore.isLoggedIn" class="empty-text">请先<a href="#" @click.prevent="$router.push('/login')">登录</a></div>
    <LoadingSpinner v-else-if="favoriteStore.loading" />

    <template v-else-if="favoriteStore.favorites.length > 0">
      <!-- 语言标签 -->
      <div class="lang-tabs">
        <button
          :class="{ active: activeLang === '' }"
          @click="activeLang = ''"
        >全部 ({{ favoriteStore.favorites.length }})</button>
        <button
          v-for="t in langTabs" :key="t.code"
          :class="{ active: activeLang === t.code }"
          @click="activeLang = t.code"
        >{{ t.name }} ({{ t.count }})</button>
      </div>

      <!-- 搜索 + 批量操作 -->
      <div class="toolbar">
        <input v-model="searchText" class="search-input" placeholder="搜索收藏的单词或释义..." />
        <button v-if="!batchMode" class="batch-enter-btn" @click="enterBatchMode">批量管理</button>
        <template v-else>
          <label class="select-all-label">
            <input type="checkbox" v-model="selectAll" @change="toggleAll" /> 全选
          </label>
          <button class="batch-btn" :disabled="selectedIds.size === 0" @click="batchRemove">取消收藏 ({{ selectedIds.size }})</button>
          <button class="batch-cancel-btn" @click="exitBatchMode">完成</button>
        </template>
      </div>

      <!-- 收藏列表 -->
      <div class="fav-list">
        <div v-for="fav in currentFavs" :key="fav.id" class="fav-item">
          <input
            v-if="batchMode"
            type="checkbox"
            :checked="selectedIds.has(fav.vocabId)"
            class="fav-checkbox"
            @change="selectedIds.has(fav.vocabId) ? selectedIds.delete(fav.vocabId) : selectedIds.add(fav.vocabId)"
          />
          <div class="fav-card" @click="toggleReveal(fav.id)">
            <div class="fav-top">
              <div class="fav-word-row">
                <span class="fav-word">{{ vocabMeta(fav.vocabId).word || '词汇已删除或加载中' }}</span>
                <span v-if="vocabMeta(fav.vocabId).phonetic" class="fav-phonetic">{{ vocabMeta(fav.vocabId).phonetic }}</span>
                <span v-if="vocabMeta(fav.vocabId).partOfSpeech" class="fav-pos">{{ vocabMeta(fav.vocabId).partOfSpeech }}</span>
                <span class="fav-lang-tag">{{ getLangName(fav.langCode) }}</span>
                <span
                  v-if="progressMeta(fav.vocabId).masteryLevel !== undefined"
                  class="fav-mastery"
                  :style="{ color: masteryColors[progressMeta(fav.vocabId).masteryLevel] || '#bbb' }"
                >{{ masteryLabels[progressMeta(fav.vocabId).masteryLevel] || '' }}</span>
              </div>
              <button class="fav-unstar" @click.stop="removeFav(fav)" title="取消收藏">★</button>
            </div>
            <div v-if="revealedIds.has(fav.id)" class="fav-revealed">
              <p class="fav-def">{{ vocabMeta(fav.vocabId).definition || '暂无释义' }}</p>
              <p v-if="vocabMeta(fav.vocabId).exampleSentence" class="fav-example">
                {{ vocabMeta(fav.vocabId).exampleSentence }}
                <span v-if="vocabMeta(fav.vocabId).exampleTranslation" class="fav-example-tr"> — {{ vocabMeta(fav.vocabId).exampleTranslation }}</span>
              </p>
            </div>
            <p v-else class="fav-hint">点击卡片显示释义和例句</p>
          </div>
        </div>
      </div>

      <p v-if="currentFavs.length === 0 && searchText" class="empty-text">没有匹配的收藏</p>
    </template>

    <p v-else class="empty-text">还没有收藏任何单词，去<a href="#" @click.prevent="$router.push('/vocabulary')">词汇库</a>看看吧</p>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 820px; margin: 0 auto; padding-bottom: 60px; }
.page-header { text-align: center; padding: 24px 0 10px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--color-text-muted); }
.page-sub strong { color: #5a7d96; }

/* 语言标签 */
.lang-tabs { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin: 16px 0; }
.lang-tabs button {
  padding: 8px 18px; border-radius: 20px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.2s; color: #666;
}
.lang-tabs button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

/* 工具栏 */
.toolbar {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: rgba(255,255,255,0.65); backdrop-filter: blur(10px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-md);
  padding: 12px 20px; margin-bottom: 16px;
}
.search-input {
  flex: 1; min-width: 180px; padding: 9px 14px; border-radius: 10px;
  border: 1.5px solid #e0e0e0; background: #fff; font-size: 14px; color: var(--color-text); outline: none;
}
.search-input:focus { border-color: #7c9db5; }
.batch-enter-btn {
  padding: 8px 18px; border-radius: 8px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.8); color: var(--color-text-secondary);
  font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.batch-enter-btn:hover { border-color: #bbb; background: #f5f5f5; }
.select-all-label { font-size: 13px; color: #666; display: flex; align-items: center; gap: 6px; cursor: pointer; }
.batch-btn {
  padding: 8px 18px; border-radius: 8px; border: 1px solid #e74c3c;
  background: #fef5f5; color: #e74c3c; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.batch-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.batch-btn:not(:disabled):hover { background: #fde8e8; }
.batch-cancel-btn {
  padding: 8px 18px; border-radius: 8px; border: 1.5px solid #5a7d96;
  background: rgba(90,125,150,0.06); color: #5a7d96;
  font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.batch-cancel-btn:hover { background: rgba(90,125,150,0.12); }

/* 列表 */
.fav-list { display: flex; flex-direction: column; gap: 10px; }
.fav-item { display: flex; align-items: flex-start; gap: 10px; }
.fav-checkbox { margin-top: 20px; width: 17px; height: 17px; cursor: pointer; flex-shrink: 0; accent-color: #5a7d96; }
.fav-card {
  flex: 1; background: rgba(255,255,255,0.72); backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-md);
  padding: 16px 20px; transition: all 0.3s;
  position: relative; min-width: 0;
}
.fav-card:hover { border-color: rgba(124,157,181,0.2); box-shadow: 0 4px 20px rgba(90,125,150,0.08); }

.fav-top { display: flex; align-items: flex-start; justify-content: space-between; }
.fav-word-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.fav-word { font-size: 19px; font-weight: 700; color: var(--color-text); font-family: var(--font-heading); }
.fav-phonetic { font-size: 13px; color: #999; }
.fav-pos { font-size: 11px; padding: 2px 8px; border-radius: 8px; background: rgba(124,92,191,0.08); color: #7c5cbf; font-weight: 500; }
.fav-lang-tag { font-size: 10px; padding: 2px 8px; border-radius: 8px; background: rgba(0,0,0,0.05); color: #888; }
.fav-mastery { font-size: 11px; font-weight: 700; }

.fav-unstar {
  background: none; border: none; font-size: 18px; color: #f0975c; cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.34,1.56,0.64,1); flex-shrink: 0;
}
.fav-unstar:hover { transform: scale(1.3); }

.fav-def { font-size: 15px; color: #555; margin-top: 8px; line-height: 1.6; }
.fav-example { font-size: 13px; color: #999; font-style: italic; margin-top: 6px; }
.fav-example-tr { color: #bbb; font-style: normal; }
.fav-hint { font-size: 13px; color: #aaa; margin-top: 8px; font-style: italic; text-align: center; }
.fav-revealed { margin-top: 8px; animation: fadeIn 0.25s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

.empty-text { text-align: center; color: var(--color-text-muted); padding: 60px 0; font-size: 15px; }
.empty-text a { color: var(--color-primary-dark); font-weight: 500; }
</style>

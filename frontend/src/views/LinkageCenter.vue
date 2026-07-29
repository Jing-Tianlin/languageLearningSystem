<script setup>
/**
 * LinkageCenter.vue — 学习诊断中心
 *
 * 功能：
 *   1. 待复习词 — 根据艾宾浩斯曲线计算哪些词该复习了
 *   2. 薄弱维度 — 雷达图中的短板 + 针对性练习入口
 *   3. 词性分布 — 用户词汇库的词性结构分析和缺口提示
 */
import { ref, onMounted, watch, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { getExamLevels } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import { toast } from '@/composables/useToast'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { LANG_NAMES } from '@/config/languages'

const authStore = useAuthStore()
const router = useRouter()
const BASE = API_BASE_URL

const currentLang = ref(authStore.targetLanguage || 'en')
const loading = ref(false)
const userId = ref(null)

// 数据
const reviewWords = ref([])     // 待复习词
const weakPoints = ref({})      // 维度薄弱点 (0~1)
const strengthPoints = ref({})  // 维度强项
const posDistribution = ref({}) // 词性分布

const dimLabels = {
  spelling: '拼写', preposition: '介词', tense: '时态',
  article: '冠词', word_order: '语序', conjugation: '变位', vocabulary: '词汇',
}

const posNames = {
  noun: '名词', verb: '动词', adjective: '形容词',
  adverb: '副词', phrase: '短语', greeting: '问候语', interjection: '感叹词',
}

watch(() => authStore.targetLanguage, () => {
  currentLang.value = authStore.targetLanguage || 'en'
  if (userId.value) loadAll()
})

onMounted(async () => {
  const uid = localStorage.getItem('userId')
  if (!uid) return
  userId.value = Number(uid)
  await loadAll()
  // 无论是否有数据都加载推荐
  loadRecommendations()
})

async function loadAll() {
  loading.value = true
  try {
    const [hwRes, wpRes] = await Promise.all([
      fetch(`${BASE}/link/hot-words?userId=${userId.value}&limit=20`).then(r => r.json()),
      fetch(`${BASE}/stats/weak-points?userId=${userId.value}`).then(r => r.json()),
    ])
    reviewWords.value = (hwRes.data || []).slice(0, 12)

    if (wpRes.code === 200 && wpRes.data) {
      const all = wpRes.data
      // 分成强项和弱项
      const weak = {}, strong = {}
      Object.entries(all).forEach(([k, v]) => {
        if (v === 0) return // 无数据
        if (v < 0.5) weak[k] = v
        else strong[k] = v
      })
      weakPoints.value = weak
      strengthPoints.value = strong
    }

    // 词性分布
    fetch(`${BASE}/link/pos-shortage?userId=${userId.value}`).then(r => r.json()).then(j => {
      // pos-shortage 返回短缺列表，我们反过来用 total vocabulary 查询
    }).catch(() => {})

    // 直接从 vocabulary 统计词性
    try {
      const vRes = await fetch(`${BASE}/vocabulary/vocabularies?langCode=${currentLang.value}&pageSize=1`)
      posDistribution.value = {} // 有数据表明有词汇
    } catch (e) {}

  } catch (e) {
    toast.error('数据加载失败')
  } finally {
    loading.value = false
  }
}

const hasData = computed(() =>
  reviewWords.value.length > 0 ||
  Object.keys(weakPoints.value).length > 0 ||
  Object.keys(strengthPoints.value).length > 0
)

// ===== 推荐算法 =====
const recommendations = ref([])
const recLoading = ref(false)

async function loadRecommendations() {
  recLoading.value = true
  const uid = userId.value
  const lang = currentLang.value
  if (!uid) { recLoading.value = false; return }

  try {
    // 1. 获取薄弱维度
    const wpRes = await fetch(BASE + '/stats/weak-points?userId=' + uid)
    const wpJson = await wpRes.json()
    const weakKeys = []
    if (wpJson.code === 200 && wpJson.data) {
      Object.entries(wpJson.data).filter(([k, v]) => v < 0.5 && v > 0).forEach(([k]) => weakKeys.push(k))
    }

    const levelNames = ['小学', '初中', '高中', 'CET4', 'CET6', '专业人士']
    const userLevel = authStore.targetLevel
    const levelLabel = userLevel !== null && userLevel !== -1 && levelNames[userLevel] ? levelNames[userLevel] : ''

    const items = []

    // 2. AI 生成今日热点推荐（失败不影响后续）
    try {
      const hotTopicsByLang = {
        en: ['科技发展', '人文故事', '生活方式', '环境保护', '教育理念'],
        ja: ['日本文化', '観光旅行', '食文化', 'アニメ', '技術革新'],
        ko: ['한국 문화', '여행', '음식', 'K-POP', '기술 발전'],
        fr: ['Culture française', 'Voyage', 'Gastronomie', 'Art', 'Science'],
        de: ['Deutsche Kultur', 'Reisen', 'Essen', 'Technologie', 'Umwelt'],
      }
      const hotTopics = hotTopicsByLang[lang] || hotTopicsByLang.en
      const randomTopic = hotTopics[Math.floor(Math.random() * hotTopics.length)]
      const aiRes = await fetch(BASE + '/ai/generate-reading', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lang, level: userLevel ?? 2, topic: '一篇适合' + levelLabel + '学习者阅读的' + randomTopic + '类短文' }),
      })
      const aiJson = await aiRes.json()
      if (aiJson.code === 200 && aiJson.data) {
        items.push({ type: '今日热点', title: aiJson.data.title || randomTopic + '相关阅读', subtitle: randomTopic + ' · AI 生成', level: aiJson.data.level || levelLabel, reason: '基于今日热点「' + randomTopic + '」AI 生成', action: 'ai-reading', actionId: null, content: aiJson.data.content, coreVocab: JSON.stringify(aiJson.data.coreVocabulary || []), quizQuestions: JSON.stringify(aiJson.data.quizQuestions || []) })
      }
    } catch (e) { /* AI 失败跳过 */ }

    // 3. 薄弱维度修复推荐（失败不影响后续）
    const dimLabels = { spelling: '拼写', preposition: '介词', tense: '时态', article: '冠词', word_order: '语序', conjugation: '变位', vocabulary: '词汇' }
    if (weakKeys.length > 0) {
      try {
        const weakLabels = weakKeys.map(k => dimLabels[k] || k).join('、')
        const aiWeakRes = await fetch(BASE + '/ai/generate-reading', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ lang, level: userLevel ?? 2, topic: '一篇帮助学生加强' + weakLabels + '的阅读训练文章，文章中多出现相关语法点' }),
        })
        const aiWeakJson = await aiWeakRes.json()
        if (aiWeakJson.code === 200 && aiWeakJson.data) {
          items.push({ type: '薄弱加强', title: aiWeakJson.data.title || weakLabels + '强化训练', subtitle: '针对 ' + weakLabels + ' 定制', level: aiWeakJson.data.level || levelLabel, reason: '薄弱维度「' + weakLabels + '」加强训练', action: 'ai-reading', actionId: null, content: aiWeakJson.data.content, coreVocab: JSON.stringify(aiWeakJson.data.coreVocabulary || []), quizQuestions: JSON.stringify(aiWeakJson.data.quizQuestions || []) })
        }
      } catch (e) { /* AI 失败跳过 */ }
    }

    // 4. 兜底：从数据库拉取文章确保至少有一个推荐
    try {
      const artRes = await fetch(BASE + '/reading/articles?langCode=' + lang)
      const artJson = await artRes.json()
      const articles = (artJson.data || []).filter(a => {
        if (userLevel !== null && userLevel !== -1) {
          return Math.abs((a.level_num || 0) - (userLevel + 1)) <= 1
        }
        return true
      })
      // 如果 AI 推荐不足 2 条，用数据库文章补上
      if (items.length < 2 && articles.length > 0) {
        articles.slice(0, 3 - items.length).forEach(a => items.push({
          type: '阅读', title: a.title, level: a.level,
          reason: userLevel !== null && userLevel !== -1 && levelNames[userLevel] ? '适合「' + levelNames[userLevel] + '」等级' : '推荐阅读',
          action: 'reading', actionId: a.id,
        }))
      }
    } catch (e) { /* 兜底失败也跳过 */ }

    // 5. 如果还完全没推荐，铁底方案：热点新闻硬编码一条
    if (items.length === 0) {
      items.push({ type: '今日热点', title: 'Try reading something new today!', subtitle: '每日推荐', level: '', reason: '开始你的阅读之旅', action: 'reading', actionId: null, content: '', coreVocab: '[]', quizQuestions: '[]' })
    }

    recommendations.value = items
  } catch (e) { recommendations.value = [] }
  finally { recLoading.value = false }
}

// loadRecommendations 由 onMounted 在 loadAll 后调用

function goReading(id) {
  router.push(`/reading?articleId=${id}`)
}
function goVocabulary() { router.push('/vocabulary') }

function goAiReading(item) {
  // AI 生成的文章暂存到 sessionStorage，阅读页读取
  sessionStorage.setItem('aiReadingArticle', JSON.stringify({
    title: item.title,
    content: item.content,
    coreVocabulary: item.coreVocab || '[]',
    quizQuestions: item.quizQuestions || '[]',
    level: item.level,
  }))
  router.push('/reading?ai=1')
}

function goGrammar() { router.push('/grammar') }
function goWriting() { router.push('/writing') }
function goPractice() { router.push('/flashcards') }
function goFlashcards() { router.push('/flashcards') }
function goWrongBook() { router.push({ path: '/flashcards', query: { wrongOnly: '1' } }) }
function goStats() { router.push('/stats') }
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle :text="(LANG_NAMES[currentLang] || '') + ' · 学习诊断'" tag="h1" />
      <p class="page-sub">发现短板，精准提升</p>
    </div>

    <LoadingSpinner v-if="loading" />

    <EmptyState
      v-else-if="!hasData"
      icon="search"
      title="暂无学习数据"
      description="完成一些练习后，这里会显示你的薄弱点和待复习词汇"
      action-text="去背单词"
      @action="goFlashcards"
    />

    <template v-else>
      <!-- ===== 第一行：待复习 + 薄弱点 ===== -->
      <div class="grid-2">
        <!-- 待复习词 -->
        <div class="card">
          <div class="card-head">
            <h3>待复习词汇</h3>
            <span class="card-badge">{{ reviewWords.length }} 个</span>
          </div>
          <div v-if="reviewWords.length > 0" class="word-scroll">
            <span v-for="w in reviewWords" :key="w.vocabId" class="word-chip" :class="{ urgent: w.masteryLevel <= 1 }">
              {{ w.word }}
            </span>
          </div>
          <p v-else class="card-empty">暂无待复习词汇</p>
          <div class="card-actions">
            <button class="btn btn-ghost btn-sm" @click="goWrongBook">错题重练</button>
            <button class="btn btn-primary btn-sm" @click="goFlashcards">去背单词</button>
          </div>
        </div>

        <!-- 薄弱维度 -->
        <div class="card">
          <div class="card-head">
            <h3>薄弱维度</h3>
          </div>
          <div v-if="Object.keys(weakPoints).length > 0" class="dim-list">
            <div v-for="(val, key) in weakPoints" :key="key" class="dim-bar row">
              <span class="dim-label">{{ dimLabels[key] || key }}</span>
              <div class="dim-track">
                <div class="dim-fill" :style="{ width: (val * 100) + '%' }" />
              </div>
              <span class="dim-pct">{{ Math.round(val * 100) }}%</span>
            </div>
          </div>
          <p v-else class="card-empty">暂未检测到薄弱维度，继续加油</p>
          <button class="btn btn-primary" @click="goGrammar">语法专项练习</button>
        </div>
      </div>

      <!-- ===== 第二行：强项 + 建议 ===== -->
      <div class="grid-2">
        <!-- 掌握良好的维度 -->
        <div class="card">
          <div class="card-head">
            <h3>掌握良好</h3>
          </div>
          <div v-if="Object.keys(strengthPoints).length > 0" class="dim-list">
            <div v-for="(val, key) in strengthPoints" :key="key" class="dim-bar row good">
              <span class="dim-label">{{ dimLabels[key] || key }}</span>
              <div class="dim-track">
                <div class="dim-fill" :style="{ width: (val * 100) + '%' }" />
              </div>
              <span class="dim-pct">{{ Math.round(val * 100) }}%</span>
            </div>
          </div>
          <p v-else class="card-empty">多练习让各项都亮起来</p>
        </div>

        <!-- 建议 -->
        <div class="card suggestion-card">
          <h3>学习建议</h3>
          <div class="suggestion-list">
            <div v-if="reviewWords.length > 0" class="sg-item">
              <span class="sg-dot warn"></span>
              <span>有 <strong>{{ reviewWords.length }}</strong> 个词汇等待复习，建议先背单词巩固记忆</span>
            </div>
            <div v-if="Object.keys(weakPoints).length > 0" class="sg-item">
              <span class="sg-dot danger"></span>
              <span>
                <strong>{{ Object.keys(weakPoints).map(k => dimLabels[k] || k).join('、') }}</strong> 方面需要加强
              </span>
            </div>
            <div v-if="Object.keys(strengthPoints).length > 0" class="sg-item">
              <span class="sg-dot ok"></span>
              <span>
                <strong>{{ Object.keys(strengthPoints).slice(0, 2).map(k => dimLabels[k] || k).join('、') }}</strong> 掌握扎实，继续保持
              </span>
            </div>
            <div class="sg-item">
              <span class="sg-dot"></span>
              <span>每天坚持练习，保持学习节奏</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 推荐区块 ===== -->
      <div class="card recommendation-card">
        <div class="card-head">
          <h3>为你推荐</h3>
          <span class="card-badge">基于学习数据智能推荐</span>
        </div>
        <div v-if="recommendations.length > 0" class="rec-list">
          <div v-for="(item, i) in recommendations" :key="i" class="rec-item"
            @click="item.action === 'reading' && item.actionId ? goReading(item.actionId) : (item.action === 'ai-reading' ? goAiReading(item) : goVocabulary())">
            <span class="rec-type" :class="item.type === '阅读' ? 'read' : 'vocab'">{{ item.type }}</span>
            <div class="rec-body">
              <span class="rec-title">{{ item.title }}</span>
              <span v-if="item.subtitle" class="rec-subtitle">{{ item.subtitle }}</span>
              <span class="rec-reason">{{ item.reason }}</span>
            </div>
            <span class="rec-arrow">→</span>
          </div>
        </div>
      </div>

      <!-- 快捷操作 -->
      <div class="quick-row">
        <button class="btn btn-ghost" @click="goPractice">巩固练习</button>
        <button class="btn btn-ghost" @click="goFlashcards">背单词</button>
        <button class="btn btn-ghost" @click="goGrammar">语法中心</button>
        <button class="btn btn-ghost" @click="goWriting">写作训练</button>
        <button class="btn btn-ghost" @click="goStats">详细分析</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 820px; margin: 0 auto; padding: 0 16px 60px; }
.page-header { text-align: center; padding: 20px 0 8px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); }
.page-sub { font-size: 14px; color: var(--color-text-muted); }
.loading { text-align: center; padding: 60px; color: #aaa; }

/* 空状态 */
.empty-block { text-align: center; padding: 60px 0; }
.empty-title { font-size: 18px; font-weight: 700; color: #999; margin: 0 0 8px; }
.empty-desc { font-size: 14px; color: #bbb; margin: 0 0 24px; }
.cta-btn {
  padding: 12px 32px; border-radius: 12px; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  font-size: 15px; font-weight: 600; cursor: pointer;
}

/* 网格 */
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 14px; }

/* 卡片 */
.card {
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.04); border-radius: 14px; padding: 20px 22px;
}
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-head h3 { font-size: 15px; font-weight: 700; color: var(--color-text); margin: 0; }
.card-badge { font-size: 11px; padding: 3px 10px; border-radius: 10px; background: rgba(90,125,150,0.08); color: #5a7d96; font-weight: 600; }
.card-actions { display: flex; gap: 8px; justify-content: center; margin-top: 14px; }
.card-empty { font-size: 13px; color: #bbb; text-align: center; padding: 16px 0; }
.card-action {
  display: block; width: 100%; margin-top: 12px; padding: 9px 0; border-radius: 8px;
  border: 1.5px solid rgba(90,125,150,0.2); background: rgba(255,255,255,0.7);
  color: #5a7d96; font-size: 13px; font-weight: 600; cursor: pointer; text-align: center;
  transition: all 0.2s;
}
.card-action:hover { background: rgba(90,125,150,0.06); border-color: rgba(90,125,150,0.4); }

/* 词汇芯片 */
.word-scroll { display: flex; flex-wrap: wrap; gap: 6px; }
.word-chip {
  padding: 4px 12px; border-radius: 12px; border: 1.5px solid #eee;
  background: #f8fafb; font-size: 13px; font-weight: 600; color: #555;
}
.word-chip.urgent { border-color: rgba(231,76,60,0.2); background: rgba(231,76,60,0.04); color: #c0392b; }

/* 维度条 */
.dim-list { display: flex; flex-direction: column; gap: 8px; }
.dim-bar { display: flex; align-items: center; gap: 8px; }
.dim-label { font-size: 12px; color: #666; width: 40px; text-align: right; flex-shrink: 0; }
.dim-track { flex: 1; height: 6px; border-radius: 3px; background: #f0f0f0; overflow: hidden; }
.dim-fill { height: 100%; border-radius: 3px; background: #e74c3c; transition: width 0.5s ease; }
.dim-bar.good .dim-fill { background: #27ae60; }
.dim-pct { font-size: 11px; color: #888; width: 32px; flex-shrink: 0; }

/* 建议 */
.suggestion-card h3 { font-size: 15px; font-weight: 700; color: var(--color-text); margin: 0 0 14px; }
.suggestion-list { display: flex; flex-direction: column; gap: 12px; }
.sg-item { display: flex; align-items: flex-start; gap: 10px; font-size: 13px; color: #666; line-height: 1.6; }
.sg-dot { width: 8px; height: 8px; border-radius: 50%; background: #ccc; flex-shrink: 0; margin-top: 5px; }
.sg-dot.warn { background: #f0c040; }
.sg-dot.danger { background: #e74c3c; }
.sg-dot.ok { background: #27ae60; }

/* 快捷操作 */
.quick-row { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-top: 8px; }
.quick-row button {
  padding: 8px 18px; border-radius: 10px; border: 1.5px solid rgba(90,125,150,0.2);
  background: rgba(255,255,255,0.7); color: #5a7d96;
  font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s;
}
.quick-row button:hover { background: rgba(90,125,150,0.06); border-color: rgba(90,125,150,0.4); }

@media (max-width: 640px) {
  .grid-2 { grid-template-columns: 1fr; }
}

/* 推荐区块 */
.recommendation-card { margin-top: 14px; }
.rec-list { display: flex; flex-direction: column; gap: 6px; }
.rec-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; border-radius: 10px;
  background: linear-gradient(135deg, rgba(90,125,150,0.03), rgba(90,125,150,0.06));
  border: 1px solid rgba(90,125,150,0.1); cursor: pointer;
  transition: all 0.2s;
}
.rec-item:hover { border-color: rgba(90,125,150,0.25); transform: translateY(-1px); }
.rec-type {
  padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; white-space: nowrap;
}
.rec-type.read { background: rgba(39,174,96,0.08); color: #27ae60; }
.rec-type.vocab { background: rgba(90,125,150,0.08); color: #5a7d96; }
.rec-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.rec-title { font-size: 14px; font-weight: 600; color: var(--color-text); }
.rec-subtitle { font-size: 12px; color: #888; }
.rec-reason { font-size: 11px; color: #aaa; }
.rec-arrow { font-size: 16px; color: #ccc; flex-shrink: 0; }
.rec-item:hover .rec-arrow { color: #5a7d96; }
</style>

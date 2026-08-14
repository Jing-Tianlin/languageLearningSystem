<script setup>
/**
 * ReadingPractice.vue — 阅读训练
 *
 * 数据库文章 + AI 生成文章 → 三遍阅读法（速读·精读·检测）→ AI 解析
 */
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useVocabularyStore } from '@/stores/vocabulary'
import { getExamLevels, getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import { toast } from '@/composables/useToast'
import { useFavoriteStore } from '@/stores/favorite'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { LANG_NAMES } from '@/config/languages'

const authStore = useAuthStore()
const route = useRoute()
const favoriteStore = useFavoriteStore()
const vocabularyStore = useVocabularyStore()
const BASE = API_BASE_URL

const currentLang = ref(authStore.targetLanguage || 'en')
watch(() => authStore.targetLanguage, (v) => { currentLang.value = v || 'en'; resetToList() })

const langFlags = { en: '🇬🇧', ja: '🇯🇵', ko: '🇰🇷', fr: '🇫🇷', de: '🇩🇪' }
const examLevels = computed(() => getExamLevels(currentLang.value))
const currentLevelLabel = computed(() => getLevelLabel(currentLang.value, authStore.targetLevel))

// 页面状态
const pageState = ref('list')
const loading = ref(false)
const articles = ref([])
const levelFilter = ref('all')
const articleLevels = computed(() => [...new Set(articles.value.map(a => a.level).filter(Boolean))])
const filteredArticles = computed(() => levelFilter.value === 'all' ? articles.value : articles.value.filter(a => a.level === levelFilter.value))
const phase = ref(1)
const article = ref(null)
const coreVocab = ref([])
const quizQuestions = ref([])
const highlightedContent = ref('')
const elapsedSec = ref(0)
let timerId = null

// 测验
const userAnswers = ref([])
const quizResult = ref(null)
const phase1Time = ref(0)

// 生词弹窗
const selectedWord = ref(null)
const wordPos = ref({ x: 0, y: 0 })

// 已收藏词汇集合（本地状态即时反馈）
const favoritedWords = ref(new Set())

// 初始化时加载收藏列表
async function loadFavorites() {
  const userId = authStore.user?.id
  if (!userId) return
  await favoriteStore.fetchFavorites({ userId, pageSize: 1000 })
  const set = new Set()
  favoriteStore.favorites.forEach(f => {
    if (f.word) set.add(f.word.toLowerCase())
    if (f.vocabWord) set.add(f.vocabWord.toLowerCase())
    if (f.vocab_id) set.add(String(f.vocab_id)) // 用 vocabId 标记
  })
  favoritedWords.value = set
}

// AI 生成
const aiTopic = ref('')
const aiGenerating = ref(false)
const showAIPanel = ref(false)

// AI 解析
const analysisResult = ref('')
const analysisLoading = ref(false)

async function analyzeArticle() {
  if (!article.value || !quizResult.value) return
  analysisLoading.value = true
  analysisResult.value = ''

  const content = article.value.content || ''
  // 按双换行拆分段落，每段之间留一个空行给 AI
  const paragraphs = content.split(/\n{2,}/).filter(p => p.trim())
  const numberedText = paragraphs.map((p, i) => `[第${i + 1}段]\n${p.trim()}`).join('\n\n')

  const wrongDetails = quizResult.value.corrections?.filter(c => !c.correct).map(c => {
    const q = quizQuestions.value[c.questionIndex]
    return `错题${c.questionIndex + 1}: ${q?.question || ''}（正确答案: ${['A','B','C','D'][c.correctAnswer]}）`
  }).join('\n') || '全部正确'

  try {
    const data = await fetchJson(`${BASE}/ai/ask`, {
      method: 'POST',
      body: {
        question: `你是${LANG_NAMES[currentLang.value] || '语言'}阅读老师。请用中文对以下测验做非常详细的解析（500字以上），包括正确和错误的题目：\n\n=== 正文（已标注段落）===\n${numberedText}\n\n=== 答题 ===\n得分: ${quizResult.value.score}/${quizResult.value.total}\n${wrongDetails}\n\n要求：\n1. 逐道题分析（包括答对的题也要分析）\n2. 每题明确指出原文依据位置（如"第2段第3句"）\n3. 引用原文关键句\n4. 指出语法点或阅读技巧\n5. 结尾给1-2个精读建议\n6. 只用中文，不要用其他语言，禁止 markdown 标记`,
        langCode: currentLang.value,
      },
    })
    analysisResult.value = data.data?.answer || 'AI 暂不可用'
  } catch (e) { analysisResult.value = '解析失败' }
  finally { analysisLoading.value = false }
}

function startTimer() { stopTimer(); elapsedSec.value = 0; timerId = setInterval(() => elapsedSec.value++, 1000) }
function stopTimer() { if (timerId) { clearInterval(timerId); timerId = null } }
onUnmounted(() => stopTimer())

onMounted(() => {
  loadFavorites()
  // 如果 URL 有 articleId 参数，直接加载那篇文章
  const articleId = route.query.articleId
  if (articleId) {
    selectArticle(Number(articleId))
  } else if (route.query.ai) {
    // 从 sessionStorage 恢复 AI 推荐文章
    const saved = sessionStorage.getItem('aiReadingArticle')
    if (saved) {
      try {
        const data = JSON.parse(saved)
        article.value = {
          id: null, title: data.title || '', content: data.content || '',
          level: data.level || '', wordCount: (data.content || '').split(/\s+/).length,
          aiGenerated: true,
        }
        coreVocab.value = JSON.parse(data.coreVocabulary || '[]')
        quizQuestions.value = JSON.parse(data.quizQuestions || '[]')
        quizAnswers.value = []; quizFinished.value = false
        highlight(); pageState.value = 'reading'; startTimer()
        sessionStorage.removeItem('aiReadingArticle')
      } catch (e) { loadArticles() }
    } else { loadArticles() }
  } else {
    loadArticles()
  }
})

async function loadArticles() {
  loading.value = true
  const params = new URLSearchParams({ langCode: currentLang.value })
  if (authStore.targetLevel !== null && authStore.targetLevel !== -1) params.set('levelNum', authStore.targetLevel)
  try {
    const json = await fetchJson(`${BASE}/reading/articles?${params}`)
    articles.value = json.code === 200 ? (json.data || []) : []
  } catch (e) { articles.value = [] }
  finally { loading.value = false }
}

function resetToList() { pageState.value = 'list'; article.value = null; stopTimer(); loadArticles() }

async function selectArticle(id) {
  loading.value = true; phase.value = 1; stopTimer()
  userAnswers.value = []; quizResult.value = null; analysisResult.value = ''
  const userId = authStore.user?.id; if (!userId) { toast.error('请先登录'); return; }
  try {
    const json = await fetchJson(`${BASE}/reading/article?userId=${userId}&langCode=${currentLang.value}&articleId=${id}`)
    if (json.code === 200 && json.data) {
      article.value = json.data
      coreVocab.value = safeParse(json.data.coreVocabulary)
      quizQuestions.value = safeParse(json.data.quizQuestions)
      quizAnswers.value = []; quizFinished.value = false
      highlight(); pageState.value = 'reading'; startTimer()
    } else { toast('文章加载失败', 'error') }
  } catch (e) { toast('网络错误', 'error') }
  finally { loading.value = false }
}

// 阅读历史
const readingHistory = ref([])
const rhLoading = ref(false)
const showReadingHistory = ref(false)

async function loadReadingHistory() {
  const userId = authStore.user?.id
  if (!userId) return
  rhLoading.value = true
  try {
    const json = await fetchJson(`${BASE}/history/reading?userId=${userId}&limit=20`)
    readingHistory.value = json.data || []
  } catch (e) { readingHistory.value = [] }
  finally { rhLoading.value = false }
}

function safeParse(raw) { try { return typeof raw === 'string' ? JSON.parse(raw) : (raw || []) } catch { return [] } }

function viewReadingDetail(r) {
  // 如果有完整文章内容，直接加载进入阅读
  if (r.article_content || r.core_vocabulary || r.quiz_questions) {
    article.value = {
      id: r.article_id,
      title: r.article_title || '',
      content: r.article_content || '',
      level: r.article_level || '',
      wordCount: r.article_content ? r.article_content.split(/\s+/).length : 0,
      aiGenerated: true,
    }
    coreVocab.value = safeParse(r.core_vocabulary || '[]')
    quizQuestions.value = safeParse(r.quiz_questions || '[]')
    quizAnswers.value = []
    quizFinished.value = false
    highlight()
    pageState.value = 'reading'
    phase.value = 1
    stopTimer()
    startTimer()
    return
  }
  // 有文章 ID 的话从数据库加载
  if (r.article_id) {
    selectArticle(r.article_id)
    return
  }
  toast('该记录没有保存文章内容', 'info')
}

function highlight() {
  let text = article.value?.content || ''
  if (text && coreVocab.value && coreVocab.value.length > 0) {
    coreVocab.value.forEach(v => {
      if (!v.word) return
      const w = v.word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      // 使用 gi 全局忽略大小写匹配
      // 注意：en/ja/ko/fr/de 的全字符都支持 (\\b 边界对 ASCII 无效但对 CJK 有效则不支持)
      const regex = new RegExp(w, 'gi')
      text = text.replace(regex, m => `<span class="hw" data-word="${m}" data-def="${v.definition || ''}">${m}</span>`)
    })
  }
  highlightedContent.value = text
}

function onArticleClick(e) {
  const s = e.target.closest('.hw')
  if (!s) { selectedWord.value = null; return }
  selectedWord.value = { word: s.dataset.word, definition: s.dataset.def }
  wordPos.value = { x: e.clientX, y: e.clientY }
}

async function addToVocab(word) {
  const userId = authStore.user?.id
  if (!userId) { toast('请先登录', 'error'); return }
  try {
    const json = await fetchJson(`${BASE}/reading/vocab-action`, {
      method: 'POST',
      body: { userId: Number(userId), word, action: 'add', langCode: currentLang.value },
    })
    if (json.code !== 200) {
      toast.error(json.message || '加入失败')
      return
    }
    // 同步本地已收藏集合
    favoritedWords.value = new Set([...favoritedWords.value, word.toLowerCase()])
    // 刷新收藏列表和词汇列表，确保收藏页可见
    await Promise.all([
      favoriteStore.fetchFavorites({ userId: Number(userId), pageSize: 500 }),
      vocabularyStore.fetchVocabularies({ pageSize: 5000 }),
    ])
    toast.success(`「${word}」已加入生词本`)
    selectedWord.value = null
  } catch (e) {
    toast.error('加入失败')
  }
}

function goToPhase2() { phase1Time.value = elapsedSec.value; phase.value = 2 }
function goToPhase3() { phase.value = 3; stopTimer() }

const quizAnswers = ref([])
const quizFinished = ref(false)

function selectAnswer(qi, oi) {
  if (quizAnswers.value.find(a => a.questionIndex === qi)) return
  const correct = oi === quizQuestions.value[qi].answer
  quizAnswers.value.push({ questionIndex: qi, selectedIndex: oi, correct })
  if (quizAnswers.value.length >= quizQuestions.value.length) { quizFinished.value = true; stopTimer() }
}

function submitQuizResult() {
  const score = quizAnswers.value.filter(a => a.correct).length
  const total = quizQuestions.value.length
  quizResult.value = {
    score,
    total,
    corrections: quizAnswers.value.map(a => ({
      questionIndex: a.questionIndex,
      correct: a.correct,
      correctAnswer: quizQuestions.value[a.questionIndex].answer,
    })),
    message: score >= total * 0.8 ? '优秀!' : score >= total * 0.6 ? '不错!' : '加油!',
  }
  phase.value = 4
  const userId = authStore.user?.id
  if (!userId) return
  // 数据库文章提交测验结果
  if (article.value?.id && !article.value.aiGenerated) {
    fetchJson(`${BASE}/reading/quiz`, {
      method: 'POST',
      body: {
        userId: Number(userId),
        articleId: article.value.id,
        answers: quizAnswers.value.map(a => a.selectedIndex),
        phase1Duration: phase1Time.value, phase2Duration: 0,
        langCode: currentLang.value,
      },
    }).catch(() => {})
  }
  // 保存阅读历史（含完整内容）
  const payload = {
    userId: Number(userId), langCode: currentLang.value,
    articleTitle: article.value.title || '',
    articleLevel: article.value.level || '',
    articleId: article.value.id || null,
    articleContent: article.value.content || '',
    coreVocabulary: JSON.stringify(coreVocab.value),
    quizQuestions: JSON.stringify(quizQuestions.value),
    quizScore: score, quizTotal: total,
  }
  fetchJson(`${BASE}/history/reading`, {
    method: 'POST', body: payload,
  }).catch(() => {})
}

async function aiGenerate() {
  aiGenerating.value = true; loading.value = true
  try {
    const json = await fetchJson(`${BASE}/ai/generate-reading`, { method: 'POST', body: { langCode: currentLang.value, level: authStore.targetLevel ?? 2, topic: aiTopic.value.trim() } })
    if (json.code === 200 && json.data) {
      article.value = json.data; coreVocab.value = safeParse(json.data.coreVocabulary); quizQuestions.value = safeParse(json.data.quizQuestions)
      quizAnswers.value = []; quizFinished.value = false
      highlight(); phase.value = 1; stopTimer(); startTimer()
      pageState.value = 'reading'; showAIPanel.value = false; aiTopic.value = ''
      toast('AI 文章已生成', 'success')
    } else toast('生成失败', 'error')
  } catch (e) { toast('网络错误', 'error') }
  finally { loading.value = false; aiGenerating.value = false }
}
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle :text="(LANG_NAMES[currentLang] || '') + ' 阅读训练'" tag="h1" />
      <p class="page-sub">
        <span class="sub-level">{{ currentLevelLabel }}</span>
        <span class="sub-divider">·</span>
        <span>三遍阅读法 · 速读 → 精读 → 检测</span>
      </p>
    </div>

    <!-- ========== 文章列表 ========== -->
    <div v-if="pageState === 'list'" class="list-view">
      <!-- AI 生成入口 -->
      <div class="ai-entry">
        <button class="btn btn-ghost" @click="showAIPanel = !showAIPanel">
          <span class="ai-entry-icon"></span>
          <span>AI 生成阅读文章</span>
          <span class="ai-entry-arrow" :class="{ open: showAIPanel }">▾</span>
        </button>
        <div v-if="showAIPanel" class="ai-panel">
          <input v-model="aiTopic" class="ai-input" placeholder="输入主题，如「旅行」「环保」... 留空则自动生成" @keyup.enter="aiGenerate" />
          <button class="btn btn-primary" :disabled="aiGenerating" @click="aiGenerate">{{ aiGenerating ? '生成中...' : '开始生成' }}</button>
        </div>
      </div>

      <LoadingSpinner v-if="loading" />

      <!-- 等级筛选 + 文章列表 -->
      <template v-else-if="articles.length > 0">
        <div class="level-filter">
          <button class="chip" :class="{ active: levelFilter === 'all' }" @click="levelFilter = 'all'">全部</button>
          <button v-for="lv in articleLevels" :key="lv" class="chip" :class="{ active: levelFilter === lv }" @click="levelFilter = lv">{{ lv }}</button>
        </div>

        <div class="article-list">
          <div v-for="a in filteredArticles" :key="a.id" class="article-item" @click="selectArticle(a.id)">
            <div class="item-left">
              <span class="item-icon"></span>
              <div class="item-body">
                <span class="item-title">{{ a.title }}</span>
                <div class="item-meta">
                  <span class="item-level">{{ a.level }}</span>
                  <span v-if="a.tags" class="item-tags">{{ a.tags }}</span>
                </div>
              </div>
            </div>
            <div class="item-right">
              <span class="item-wc">{{ a.wordCount }} 词</span>
              <span class="item-arrow">→</span>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="empty-state">
        <span class="empty-icon"></span>
        <p class="empty-title">该等级暂无文章</p>
        <p class="empty-desc">试试「AI 生成文章」或在学习中心切换等级</p>
      </div>

      <!-- 阅读历史 -->
      <div class="history-block">
        <button class="btn btn-ghost btn-block" @click="showReadingHistory = !showReadingHistory; if (showReadingHistory) loadReadingHistory()">
          学习记录
          <span class="toggle-arrow" :class="{ open: showReadingHistory }">▾</span>
        </button>
        <div v-if="showReadingHistory" class="history-list">
          <div v-if="rhLoading" class="loading-text">加载中...</div>
          <div v-else-if="readingHistory.length === 0" class="loading-text">暂无记录</div>
          <div v-for="r in readingHistory" :key="r.id" class="history-item" @click="viewReadingDetail(r)">
            <span class="hi-title">{{ r.article_title }}</span>
            <span class="hi-score">{{ r.quiz_score }}/{{ r.quiz_total }}</span>
            <span class="hi-time">{{ (r.completed_at || '').substring(0, 10) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 阅读训练 ========== -->
    <div v-if="pageState === 'reading'" class="reading-view">
      <!-- 顶栏 -->
      <div class="top-bar">
        <button class="btn btn-secondary btn-sm" @click="resetToList">← 文章列表</button>
        <div class="top-info">
          <span class="top-level">{{ article?.level }}</span>
          <span class="top-divider">·</span>
          <span class="top-wc">{{ article?.wordCount }} 词</span>
          <span v-if="article?.aiGenerated" class="top-ai-badge">AI 生成</span>
        </div>
        <span class="top-timer" v-if="phase <= 2"> {{ elapsedSec }}s</span>
      </div>

      <!-- 阶段指示器 -->
      <div class="phase-indicator">
        <div class="phase-step" :class="{ active: phase >= 1, done: phase > 1 }" @click="phase = 1">
          <span class="phase-num">1</span>
          <span class="phase-label">速读</span>
        </div>
        <div class="phase-line" :class="{ fill: phase > 1 }" />
        <div class="phase-step" :class="{ active: phase >= 2, done: phase > 2 }" @click="phase >= 2 && (phase = 2)">
          <span class="phase-num">2</span>
          <span class="phase-label">精读</span>
        </div>
        <div class="phase-line" :class="{ fill: phase > 2 }" />
        <div class="phase-step" :class="{ active: phase >= 3, done: phase > 3 }" @click="phase >= 3 && (phase = 3)">
          <span class="phase-num">3</span>
          <span class="phase-label">检测</span>
        </div>
        <div class="phase-line" :class="{ fill: phase > 3 }" />
        <div class="phase-step" :class="{ active: phase >= 4 }">
          <span class="phase-num">✓</span>
          <span class="phase-label">结果</span>
        </div>
      </div>

      <LoadingSpinner v-if="loading" />

      <template v-else-if="article">
        <!-- ===== 速读 ===== -->
        <div v-if="phase === 1" class="reading-card">
          <h2 class="reading-title">{{ article.title }}</h2>
          <div class="reading-body" :class="{ en: currentLang === 'en' }">{{ article.content }}</div>
          <button class="btn btn-primary" @click="goToPhase2">
            <span>进入精读</span>
            <span class="btn-arrow">→</span>
          </button>
        </div>

        <!-- ===== 精读 ===== -->
        <div v-if="phase === 2" class="reading-card">
          <h2 class="reading-title">{{ article.title }}</h2>
          <div class="reading-body" v-html="highlightedContent" @click="onArticleClick" />
          <p class="reading-hint"> 点击黄色高亮词查看释义并加入生词本</p>

          <div v-if="coreVocab.length > 0" class="vocab-section">
            <h3 class="vs-title">核心生词 · {{ coreVocab.length }} 个</h3>
            <div class="vocab-list">
              <div v-for="v in coreVocab" :key="v.word" class="vl-item">
                <div class="vl-main">
                  <div class="vl-word-row">
                    <span class="vl-word">{{ v.word }}</span>
                    <span v-if="v.phonetic" class="vl-phonetic">{{ v.phonetic }}</span>
                    <span v-if="v.partOfSpeech" class="vl-pos">{{ v.partOfSpeech }}</span>
                  </div>
                  <span class="vl-def">{{ v.definition }}</span>
                </div>
                <button
                  class="btn btn-icon btn-ghost"
                  :class="{ added: favoritedWords.has(v.word.toLowerCase()) }"
                  @click="addToVocab(v.word)"
                  :title="favoritedWords.has(v.word.toLowerCase()) ? '已加入生词本' : '加入生词本'"
                >
                  <svg v-if="favoritedWords.has(v.word.toLowerCase())" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                </button>
              </div>
            </div>
          </div>

          <button class="btn btn-primary" @click="goToPhase3">
            <span>开始答题</span>
            <span class="btn-arrow">→</span>
          </button>
        </div>

        <!-- ===== 检测 ===== -->
        <div v-if="phase === 3" class="quiz-card">
          <h3 class="quiz-title">阅读理解 · 共 {{ quizQuestions.length }} 题</h3>
          <p v-if="!quizFinished" class="quiz-sub">点击选项即时判断，全部答完自动显示结果</p>

          <div v-for="(q, qi) in quizQuestions" :key="qi" class="quiz-item">
            <p class="q-text">{{ qi + 1 }}. {{ q.question }}</p>
            <div class="q-options">
              <div
                v-for="(opt, oi) in q.options" :key="oi"
                class="q-opt"
                :class="{
                  selected: quizAnswers.find(a => a.questionIndex === qi)?.selectedIndex === oi,
                  'is-correct': quizFinished && oi === q.answer,
                  'is-wrong': quizFinished && quizAnswers.find(a => a.questionIndex === qi)?.selectedIndex === oi && oi !== q.answer,
                  disabled: quizAnswers.find(a => a.questionIndex === qi),
                }"
                @click="selectAnswer(qi, oi)"
              >
                <span class="q-opt-letter">{{ ['A','B','C','D'][oi] }}</span>
                <span class="q-opt-text">{{ opt }}</span>
                <span v-if="quizFinished && oi === q.answer" class="q-icon ok">✓</span>
                <span v-if="quizFinished && quizAnswers.find(a => a.questionIndex === qi)?.selectedIndex === oi && oi !== q.answer" class="q-icon err">✗</span>
              </div>
            </div>
          </div>

          <button v-if="quizFinished" class="btn btn-primary" @click="submitQuizResult">查看结果</button>
        </div>

        <!-- ===== 结果 ===== -->
        <div v-if="phase === 4 && quizResult" class="result-card">
          <div class="result-hero">
            <span class="result-emoji">{{ quizResult.score >= quizResult.total * 0.8 ? '' : quizResult.score >= quizResult.total * 0.6 ? '' : '' }}</span>
            <div class="result-score">{{ quizResult.score }} <span class="result-of">/ {{ quizResult.total }}</span></div>
            <div class="result-label">{{ quizResult.message }}</div>
          </div>

          <!-- 错题回顾 -->
          <div v-if="quizResult.corrections && quizResult.corrections.some(c => !c.correct)" class="corrections-section">
            <h4>错题回顾</h4>
            <div v-for="c in quizResult.corrections.filter(x => !x.correct)" :key="c.questionIndex" class="corr-item">
              <div class="corr-head">
                <span class="corr-badge">✗ 第{{ c.questionIndex + 1 }}题</span>
                <span class="corr-answer">正确答案: {{ ['A','B','C','D'][c.correctAnswer] }}</span>
              </div>
            </div>
          </div>

          <!-- 操作 -->
          <div class="result-actions">
            <button class="btn btn-primary" @click="resetToList">换一篇文章</button>
            <button class="btn btn-secondary" @click="phase = 1; startTimer(); quizFinished = false; quizAnswers = []">重读本文</button>
          </div>

          <!-- AI 解析 -->
          <div class="analysis-block">
            <button class="btn btn-secondary" :disabled="analysisLoading" @click="analyzeArticle">
              <span></span>
              <span>{{ analysisLoading ? 'AI 解析中...' : analysisResult ? '重新解析' : 'AI 阅读解析' }}</span>
            </button>
            <div v-if="analysisResult" class="analysis-content">{{ analysisResult }}</div>
          </div>
        </div>
      </template>
    </div>

    <!-- 生词弹窗 -->
    <Teleport to="body">
      <div v-if="selectedWord" class="word-overlay" @click="selectedWord = null">
        <div class="word-popup" :style="{ left: wordPos.x + 'px', top: wordPos.y + 'px' }" @click.stop>
          <div class="wp-head">
            <strong>{{ selectedWord.word }}</strong>
            <button class="btn btn-icon btn-ghost" @click="selectedWord = null">×</button>
          </div>
          <p class="wp-def">{{ selectedWord.definition }}</p>
          <button class="btn btn-primary btn-sm" @click="addToVocab(selectedWord.word)">+ 加入生词本</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ====== 页面容器 ====== */
.page-wrap { max-width: 780px; margin: 0 auto; padding: 0 16px 60px; }
.page-header { text-align: center; padding: 20px 0 8px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 700; font-family: var(--font-heading); color: var(--color-text); }
.page-sub { font-size: 13px; color: var(--color-text-muted); display: flex; align-items: center; gap: 6px; justify-content: center; }
.sub-level { color: var(--color-gold-deep); font-weight: 700; }
.sub-divider { color: var(--color-text-muted); }

/* ====== 列表视图 ====== */
.list-view { margin-top: 12px; }

.ai-entry { margin-bottom: 16px; }
.ai-entry-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-glass); color: var(--color-primary);
  font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s;
}
.ai-entry-btn:hover { border-color: var(--color-border-hover); background: rgba(42, 36, 56, 0.03); }
.ai-entry-icon { font-size: 16px; }
.ai-entry-arrow { font-size: 12px; transition: transform 0.25s; }
.ai-entry-arrow.open { transform: rotate(180deg); }

.ai-panel {
  margin-top: 10px; display: flex; gap: 8px;
  padding: 12px; border-radius: var(--radius-sm); background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
}
.ai-input {
  flex: 1; padding: 10px 14px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: #fffdf4; font-size: 14px; color: var(--color-text); outline: none;
}
.ai-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(255, 107, 107, 0.12); }
.ai-submit {
  padding: 10px 20px; border-radius: var(--radius-sm); border: none;
  background: var(--gradient-primary); color: #fff;
  font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; transition: all 0.25s;
}
.ai-submit:hover:not(:disabled) { opacity: 0.92; transform: translateY(-1px); }
.ai-submit:disabled { opacity: 0.4; cursor: not-allowed; }

/* 等级筛选 */
.level-filter { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.chip {
  padding: 6px 16px; border-radius: var(--radius-full);
  border: 1px solid var(--color-border); background: var(--color-bg-glass);
  color: var(--color-text-secondary); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.2s;
}
.chip:hover { border-color: var(--color-border-hover); color: var(--color-text); }
.chip.active { background: var(--color-primary); border-color: var(--color-primary); color: #fff; }

/* 文章列表 */
.article-list { display: flex; flex-direction: column; gap: 6px; }
.article-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-radius: var(--radius-lg);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border); cursor: pointer; transition: all 0.2s;
  box-shadow: var(--shadow-sm);
}
.article-item:hover { border-color: var(--color-border-hover); box-shadow: var(--shadow-md); transform: translateY(-2px); }
.item-left { display: flex; align-items: center; gap: 12px; }
.item-icon { font-size: 20px; }
.item-title { font-size: 15px; font-weight: 700; color: var(--color-text); }
.item-meta { display: flex; gap: 6px; margin-top: 4px; }
.item-level { font-size: 11px; padding: 2px 8px; border-radius: 8px; background: rgba(255, 107, 107, 0.12); color: var(--color-primary); font-weight: 600; }
.item-tags { font-size: 11px; color: var(--color-text-muted); }
.item-right { display: flex; align-items: center; gap: 10px; }
.item-wc { font-size: 13px; color: var(--color-text-secondary); }
.item-arrow { font-size: 16px; color: var(--color-text-muted); transition: color 0.2s; }
.article-item:hover .item-arrow { color: var(--color-primary); }

/* 空状态 */
.empty-state { text-align: center; padding: 48px 0; }
.empty-icon { font-size: 40px; display: block; margin-bottom: 12px; }
.empty-title { font-size: 16px; font-weight: 700; color: var(--color-text-secondary); margin: 0 0 4px; }
.empty-desc { font-size: 13px; color: var(--color-text-muted); margin: 0; }

/* ====== 阅读视图 ====== */
.reading-view { margin-top: 8px; }

/* 顶栏 */
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 0; margin-bottom: 12px;
}
.back-link {
  padding: 6px 14px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-glass); font-size: 13px; color: var(--color-text-secondary); cursor: pointer;
  transition: all 0.2s; text-decoration: none;
}
.back-link:hover { border-color: var(--color-primary); color: var(--color-primary); }
.top-info { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.top-level { color: var(--color-primary); font-weight: 700; }
.top-divider { color: var(--color-text-muted); }
.top-wc { color: var(--color-text-secondary); }
.top-ai-badge { font-size: 11px; padding: 2px 8px; border-radius: 8px; background: rgba(255, 107, 107, 0.14); color: var(--color-gold-deep); font-weight: 600; }
.top-timer { font-size: 14px; font-weight: 700; color: var(--color-primary); }

/* 阶段指示器 */
.phase-indicator { display: flex; align-items: center; justify-content: center; gap: 0; margin-bottom: 20px; }
.phase-step {
  display: flex; flex-direction: column; align-items: center; gap: 4px; cursor: pointer;
  padding: 0 8px;
}
.phase-num {
  width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: rgba(42, 36, 56, 0.1); color: var(--color-text-muted); font-size: 13px; font-weight: 700; transition: all 0.3s;
}
.phase-step.active .phase-num { background: var(--color-gold); color: #fff; }
.phase-step.done .phase-num { background: var(--color-primary); color: #fff; }
.phase-label { font-size: 11px; color: var(--color-text-muted); font-weight: 500; }
.phase-step.active .phase-label, .phase-step.done .phase-label { color: var(--color-text-secondary); }
.phase-line { width: 40px; height: 2px; background: var(--color-border); border-radius: 1px; transition: background 0.3s; }
.phase-line.fill { background: var(--color-primary); }

/* 阅读卡片 */
.reading-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 32px;
  box-shadow: var(--shadow-sm);
}
.reading-title { font-size: 22px; font-weight: 700; font-family: var(--font-heading); color: var(--color-text); margin: 0 0 16px; line-height: 1.4; }
.reading-body { font-family: var(--font-body); font-size: 16px; color: var(--color-text); line-height: 1.9; white-space: pre-wrap; }
.reading-body.en { font-family: var(--font-english); font-size: 17px; }
.reading-body :deep(.hw) { background: linear-gradient(180deg, transparent 58%, rgba(201, 155, 115, 0.3) 58%); padding: 1px 3px; border-radius: 3px; cursor: pointer; transition: background 0.15s; }
.reading-body :deep(.hw:hover) { background: rgba(201, 155, 115, 0.42); }
.reading-hint { text-align: center; font-size: 13px; color: var(--color-text-muted); margin-top: 16px; }

/* 生词列表 */
.vocab-section { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--color-border); }
.vs-title { font-size: 14px; font-weight: 700; color: var(--color-primary); margin: 0 0 14px; }

.vocab-list { display: flex; flex-direction: column; gap: 6px; }

.vl-item {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 16px; border-radius: var(--radius-sm);
  background: #fffdf4; border: 1px solid var(--color-border);
  transition: border-color 0.15s;
}
.vl-item:hover { border-color: var(--color-border-hover); }

.vl-main { flex: 1; min-width: 0; }
.vl-word-row { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.vl-word { font-weight: 700; font-size: 15px; color: var(--color-text); }
.vl-phonetic { font-size: 12px; color: var(--color-text-muted); }
.vl-pos {
  font-size: 10px; padding: 2px 8px; border-radius: 4px;
  background: rgba(255, 107, 107, 0.14); color: var(--color-gold-deep); font-weight: 500;
}
.vl-def { font-size: 13px; color: var(--color-text-secondary); line-height: 1.5; }

.vl-add {
  width: 32px; height: 32px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-card); color: var(--color-primary); cursor: pointer; flex-shrink: 0; margin-top: 2px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.vl-add:hover { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.vl-add.added { background: rgba(255, 107, 107, 0.12); color: var(--color-primary); border-color: rgba(255, 107, 107, 0.4); }
.vl-add.added:hover { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }

/* 检测卡片 */
.quiz-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 28px;
  box-shadow: var(--shadow-sm);
}
.quiz-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin: 0 0 4px; }
.quiz-sub { font-size: 13px; color: var(--color-text-muted); margin: 0 0 24px; }
.quiz-item { margin-bottom: 22px; }
.q-text { font-size: 15px; font-weight: 600; color: var(--color-text); margin: 0 0 10px; line-height: 1.5; }
.q-options { display: flex; flex-direction: column; gap: 6px; }
.q-opt {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: var(--radius-sm);
  border: 1px solid var(--color-border); background: #fffdf4; cursor: pointer; transition: all 0.15s;
}
.q-opt:hover:not(.disabled) { border-color: var(--color-primary); background: rgba(255, 107, 107, 0.04); }
.q-opt.selected { border-color: var(--color-primary); background: rgba(255, 107, 107, 0.08); }
.q-opt.is-correct { border-color: var(--color-primary); background: rgba(255, 107, 107, 0.1); }
.q-opt.is-wrong { border-color: #a85a4c; background: rgba(255, 107, 107, 0.08); }
.q-opt.disabled { cursor: default; }
.q-opt-letter {
  width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: rgba(42, 36, 56, 0.12); font-size: 11px; font-weight: 700; color: var(--color-text-secondary); flex-shrink: 0;
}
.q-opt.selected .q-opt-letter { background: var(--color-primary); color: #fff; }
.q-opt.is-correct .q-opt-letter { background: var(--color-primary); color: #fff; }
.q-opt.is-wrong .q-opt-letter { background: #a85a4c; color: #fff; }
.q-opt-text { font-size: 14px; color: var(--color-text); flex: 1; }
.q-icon { font-size: 16px; font-weight: 700; }
.q-icon.ok { color: var(--color-primary); }
.q-icon.err { color: #a85a4c; }

/* 结果 */
.result-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 36px 32px;
  text-align: center; box-shadow: var(--shadow-sm);
}
.result-hero { margin-bottom: 24px; }
.result-emoji { font-size: 40px; display: block; margin-bottom: 8px; }
.result-score { font-size: 48px; font-weight: 800; color: var(--color-primary); font-family: var(--font-heading); }
.result-of { font-size: 22px; font-weight: 400; color: var(--color-text-muted); }
.result-label { font-size: 18px; font-weight: 700; color: var(--color-text); margin-top: 6px; }

.corrections-section { text-align: left; margin-bottom: 24px; }
.corrections-section h4 { font-size: 14px; font-weight: 700; color: var(--color-text); margin: 0 0 10px; }
.corr-item { padding: 10px 14px; border-radius: var(--radius-sm); background: rgba(255, 107, 107, 0.08); margin-bottom: 6px; }
.corr-head { display: flex; justify-content: space-between; font-size: 13px; }
.corr-badge { color: #a85a4c; font-weight: 600; }
.corr-answer { color: var(--color-primary); font-weight: 600; }

.result-actions { display: flex; gap: 10px; justify-content: center; margin-bottom: 20px; }

/* 按钮 */
.action-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 12px 32px; border-radius: 10px; border: none;
  background: var(--gradient-primary); color: #fff;
  font-size: 15px; font-weight: 600; cursor: pointer; transition: all 0.25s;
  margin: 20px auto 0; width: fit-content;
}
.action-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(255, 107, 107, 0.25); }
.btn-arrow { font-size: 16px; }
.outline-btn {
  padding: 11px 24px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-card); color: var(--color-text-secondary); font-size: 14px; font-weight: 600; cursor: pointer;
  transition: all 0.2s;
}
.outline-btn:hover { border-color: var(--color-primary); color: var(--color-primary); background: rgba(255, 107, 107, 0.04); }

/* AI 解析 */
.analysis-block { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--color-border); }
.analysis-trigger {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-glass); color: var(--color-primary);
  font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s;
}
.analysis-trigger:hover:not(:disabled) { border-color: var(--color-primary); background: rgba(255, 107, 107, 0.04); }
.analysis-trigger:disabled { opacity: 0.4; cursor: not-allowed; }
.analysis-content {
  margin-top: 12px; padding: 14px 18px; background: #fffdf4; border-radius: var(--radius-sm);
  border: 1px solid var(--color-border); font-size: 14px; color: var(--color-text-secondary); line-height: 1.8; text-align: left; white-space: pre-wrap;
}

/* 生词弹窗 */
.word-overlay { position: fixed; inset: 0; z-index: 9999; }
.word-popup {
  position: fixed; transform: translate(-50%, -120%); background: var(--color-bg-card);
  border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 16px;
  box-shadow: var(--shadow-lg); min-width: 220px; max-width: 300px;
}
.wp-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.wp-head strong { font-size: 16px; font-family: var(--font-heading); color: var(--color-text); }
.wp-close { width: 24px; height: 24px; border-radius: 50%; border: none; background: rgba(42, 36, 56, 0.06); color: var(--color-text-secondary); font-size: 14px; cursor: pointer; }
.wp-def { font-size: 13px; color: var(--color-text-secondary); line-height: 1.6; margin: 0 0 10px; }
.wp-add { width: 100%; padding: 8px 0; border-radius: var(--radius-sm); border: none; background: var(--color-primary); color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.wp-add:hover { background: var(--color-primary-dark); }

/* 历史区块 */
.history-block { margin-top: 24px; }
.history-toggle {
  display: flex; align-items: center; justify-content: space-between; width: 100%;
  padding: 10px 16px; border-radius: var(--radius-sm); border: 1px solid var(--color-border);
  background: var(--color-bg-glass); font-size: 13px; font-weight: 600; color: var(--color-text-secondary); cursor: pointer;
}
.toggle-arrow { transition: transform 0.25s; font-size: 12px; }
.toggle-arrow.open { transform: rotate(180deg); }
.history-list { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.history-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px;
  border-radius: var(--radius-sm); background: #fffdf4; border: 1px solid var(--color-border);
}
.hi-title { flex: 1; font-size: 13px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hi-type, .hi-score { font-size: 12px; color: var(--color-primary); font-weight: 600; }
.hi-time { font-size: 11px; color: var(--color-text-muted); white-space: nowrap; }
.loading-text { text-align: center; padding: 16px; color: var(--color-text-muted); font-size: 13px; }
</style>

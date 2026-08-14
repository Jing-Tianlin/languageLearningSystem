<script setup>
/**
 * FlashcardsPage.vue — 背单词（优化版）
 *
 * 三种模式:
 *   卡片模式 — 翻转卡片，看到单词回忆释义
 *   拼写模式 — 看到释义，输入单词
 *
 * 新增功能:
 *   1. 犹豫时间记录 — 跟踪用户反应时间，用于掌握度计算
 *   2. 智能选词 — 基于艾宾浩斯遗忘曲线，混合待复习/新词/巩固词
 *   3. 发音功能 — 使用 Web Speech API 朗读单词和例句
 *   4. 多种学习模式 — mix/review/new/weak
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useFavoriteStore } from '@/stores/favorite'
import { useLanguageStore } from '@/stores/language'
import { useProgressStore } from '@/stores/progress'
import { getExamLevels } from '@/data/examLevels'
import { toast } from '@/composables/useToast'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import { vocabularyApi } from '@/api/vocabulary'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import PracticeSession from '@/components/practice/PracticeSession.vue'
import { LANG_NAMES } from '@/config/languages'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const vocabularyStore = useVocabularyStore()
const favoriteStore = useFavoriteStore()
const languageStore = useLanguageStore()
const progressStore = useProgressStore()

const BASE = API_BASE_URL

const savedSettings = JSON.parse(localStorage.getItem('flashcards-settings') || '{}')
const savedMode = savedSettings.mode === 'choice' ? 'card' : savedSettings.mode
const currentLang = ref(authStore.targetLanguage || savedSettings.langCode || 'en')
const currentLevel = ref(authStore.targetLevel !== null && authStore.targetLevel !== undefined ? authStore.targetLevel : (savedSettings.level !== undefined ? savedSettings.level : -1))
const studyMode = ref(savedSettings.studyMode || 'mix')
const step = ref('setup')
const poolSize = ref(savedSettings.poolSize || 20)
const mode = ref(savedMode || 'card')
const flipped = ref(false)
const wrongOnly = ref(route.query.wrongOnly === '1')

function toggleWrongOnly() {
  wrongOnly.value = !wrongOnly.value
  if (wrongOnly.value) {
    router.replace({ query: { ...route.query, wrongOnly: '1' } })
    start()
  } else {
    router.replace({ query: {} })
    step.value = 'setup'
  }
}

function saveSettings() {
  localStorage.setItem('flashcards-settings', JSON.stringify({
    langCode: currentLang.value,
    level: currentLevel.value,
    studyMode: studyMode.value,
    poolSize: poolSize.value,
    mode: mode.value
  }))
}

watch([currentLevel, studyMode, poolSize, mode], () => {
  saveSettings()
})

watch(currentLevel, (newLevel) => {
  if (authStore.targetLevel !== newLevel) {
    authStore.setTargetLevel(newLevel)
  }
})

const words = ref([])
const index = ref(0)
const correctList = ref([])
const wrongSet = ref(new Set())
const autoAddedFav = ref(new Set())
const loading = ref(false)

const cardStartTime = ref(null)
const hesitationMs = ref(0)

const spellInput = ref('')
const spellFeedback = ref(null)

const speaking = ref(false)

const todayReviewCount = ref(0)

const examLevels = computed(() => getExamLevels(currentLang.value))
const currentLevelLabel = computed(() => {
  if (currentLevel.value === -1) return '全部等级'
  const lv = examLevels.value.find(l => l.value === currentLevel.value)
  return lv ? `${lv.examLabel}` : '全部'
})

const currentWord = computed(() => words.value[index.value] || null)
const progress = computed(() => words.value.length > 0 ? Math.round(((index.value + 1) / words.value.length) * 100) : 0)
const accuracy = computed(() => {
  const total = correctList.value.length + wrongSet.value.size
  return total > 0 ? Math.round((correctList.value.length / total) * 100) : 0
})

const studyModeLabels = {
  mix: '混合学习',
  review: '复习模式',
  new: '新词模式',
  weak: '薄弱词模式'
}

const recentStats = ref({ total: 0, mastered: 0, studying: 0 })

watch(() => authStore.targetLanguage, v => { currentLang.value = v || 'en' })

async function loadTodayReviewCount() {
  if (!authStore.isLoggedIn || !authStore.user) return
  try {
    const json = await fetchJson(`${BASE}/progress/today-count?userId=${authStore.user.id}&langCode=${currentLang.value}`)
    if (json.code === 200 && json.data) {
      todayReviewCount.value = json.data.todayReviewCount || 0
    }
  } catch (e) { /* 静默 */ }
}

onMounted(async () => {
  await languageStore.fetchLanguages()
  loadTodayReviewCount()
  if (authStore.isLoggedIn && authStore.user) {
    try {
      const json = await fetchJson(`${BASE}/stats/overview?userId=${authStore.user.id}`)
      if (json.code === 200 && json.data) recentStats.value = json.data
    } catch (e) { /* 静默 */ }
  }
})

function speak(text, lang = currentLang.value) {
  if (speaking.value || !text) return
  
  const langMap = {
    en: 'en-US',
    ja: 'ja-JP',
    ko: 'ko-KR',
    fr: 'fr-FR',
    de: 'de-DE'
  }
  
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = langMap[lang] || 'en-US'
  utterance.rate = 0.85
  
  utterance.onstart = () => { speaking.value = true }
  utterance.onend = () => { speaking.value = false }
  utterance.onerror = () => { speaking.value = false }
  
  speechSynthesis.speak(utterance)
}

function speakWord() {
  if (currentWord.value) {
    speak(currentWord.value.word)
  }
}

function speakExample() {
  if (currentWord.value?.exampleSentence) {
    speak(currentWord.value.exampleSentence)
  }
}

function stopSpeaking() {
  speechSynthesis.cancel()
  speaking.value = false
}



async function start() {
  loading.value = true
  flipReset()
  try {
    const userId = authStore.user?.id || null

    // ====== 错题本专项模式 ======
    if (wrongOnly.value) {
      const wrongParams = new URLSearchParams({ langCode: currentLang.value })
      if (userId) wrongParams.append('userId', userId)
      const wrongJson = await fetchJson(`${BASE}/stats/wrong-words?${wrongParams}`)
      if (wrongJson.code === 200 && wrongJson.data && wrongJson.data.length > 0) {
        const wrongList = wrongJson.data.sort(() => Math.random() - 0.5)
        words.value = wrongList.slice(0, Math.min(poolSize.value, wrongList.length))
      } else {
        toast.info('错题本空空如也，先去学习积累吧')
        wrongOnly.value = false
        router.replace({ query: {} })
        loading.value = false
        return
      }
    } else {
      const params = new URLSearchParams({
        langCode: currentLang.value,
        count: poolSize.value,
        mode: studyMode.value
      })
      if (userId) params.append('userId', userId)

      const json = await fetchJson(`${BASE}/vocabulary/smart-select?${params}`)

      if (json.code === 200 && json.data && json.data.length > 0) {
        words.value = json.data
      } else {
        const fallbackParams = { langCode: currentLang.value, pageSize: 500 }
        if (currentLevel.value !== -1) {
          const lv = examLevels.value.find(l => l.value === currentLevel.value)
          fallbackParams.level = lv ? lv.examLabel : null
        }
        await vocabularyStore.fetchVocabularies(fallbackParams)
        let pool = vocabularyStore.vocabularyList.filter(v => v.word && v.definition)
        if (pool.length < 4) {
          toast.warning(`词汇不足（${pool.length}个），请去词汇库添加`)
          loading.value = false; return
        }
        const count = Math.min(poolSize.value, pool.length)
        words.value = pool.sort(() => Math.random() - 0.5).slice(0, count)
      }
    }
    
    if (authStore.isLoggedIn && authStore.user) {
      await favoriteStore.fetchFavorites({ userId: authStore.user.id, pageSize: 500 })
    }
    
    cardStartTime.value = Date.now()
    step.value = 'studying'
    if (mode.value === 'listen' && currentWord.value) {
      buildListenOptions()
      setTimeout(() => speakWord(), 150)
    }
  } catch (e) { toast.error('加载失败') }
  finally { loading.value = false }
}

function flipReset() {
  words.value = []; index.value = 0; correctList.value = []; wrongSet.value = new Set()
  autoAddedFav.value = new Set(); flipped.value = false; cardStartTime.value = null
  spellInput.value = ''; spellFeedback.value = null
  listenOptions.value = []; listenAnswer.value = null; listenCorrect.value = null
  practiceResult.value = null
}

function resetCardState() {
  flipped.value = false; spellInput.value = ''; spellFeedback.value = null
  cardStartTime.value = Date.now()
  if (mode.value === 'listen' && currentWord.value) {
    buildListenOptions()
    setTimeout(() => speakWord(), 150)
  }
}

function shuffleArr(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]] }
  return a
}

// ====== 听音辨义模式 ======
const listenOptions = ref([])
const listenAnswer = ref(null)   // 已选中的释义
const listenCorrect = ref(null)  // null | true | false

function buildListenOptions() {
  const w = currentWord.value
  if (!w) return
  const distractorPool = []
  const all = vocabularyStore.vocabularyList || []
  for (const v of all) {
    if (v.id !== w.id && v.definition && !distractorPool.includes(v.definition)) distractorPool.push(v.definition)
    if (distractorPool.length >= 3) break
  }
  if (distractorPool.length < 3) {
    for (const v of words.value) {
      if (v.id !== w.id && v.definition && !distractorPool.includes(v.definition)) distractorPool.push(v.definition)
      if (distractorPool.length >= 3) break
    }
  }
  listenOptions.value = shuffleArr([w.definition, ...distractorPool.slice(0, 3)])
  listenAnswer.value = null
  listenCorrect.value = null
}

async function handleListenAnswer(opt) {
  if (listenAnswer.value !== null) return
  const w = currentWord.value
  if (!w) return
  listenAnswer.value = opt
  const isCorrect = opt === w.definition
  listenCorrect.value = isCorrect
  const hesMs = cardStartTime.value ? Date.now() - cardStartTime.value : 0

  if (isCorrect) {
    correctList.value.push(w)
    speak(w.word)
    setTimeout(() => {
      reportToBackend(w.id, hesToQuality(hesMs, true), hesMs)
      advance()
    }, 900)
  } else {
    wrongSet.value.add(w.id)
    await reportToBackend(w.id, QUALITY_FORGET, hesMs)
    if (authStore.isLoggedIn && !favoriteStore.isFavorite(w.id)) {
      try {
        await favoriteStore.addFavorite(w.id, w.langCode || currentLang.value)
        autoAddedFav.value.add(w.id)
      } catch (e) { /* 静默 */ }
    }
  }
}

function listenWrongNext() {
  listenAnswer.value = null
  listenCorrect.value = null
  advance()
}

function flip() { 
  if (mode.value === 'card') {
    flipped.value = !flipped.value
    if (flipped.value && !cardStartTime.value) {
      cardStartTime.value = Date.now()
    }
  }
}

// SRS 自评质量：1=忘记, 3=模糊, 4=认识, 5=掌握
const QUALITY_FORGET = 1
const QUALITY_VAGUE = 3
const QUALITY_KNOWN = 4
const QUALITY_MASTERED = 5

function hesToQuality(hesMs, correct) {
  if (!correct) return QUALITY_FORGET
  if (hesMs <= 0) return QUALITY_KNOWN
  if (hesMs < 1000) return QUALITY_MASTERED
  if (hesMs < 3000) return QUALITY_KNOWN
  if (hesMs < 6000) return QUALITY_VAGUE
  return QUALITY_FORGET
}

async function reportToBackend(vocabId, quality, hesMs = 0) {
  const userId = authStore.user?.id || localStorage.getItem('userId')
  if (!userId) return
  try {
    await fetchJson(`${BASE}/practice/record`, {
      method: 'POST',
      body: {
        userId: Number(userId),
        vocabId,
        langCode: currentLang.value,
        quality,
        hesitationMs: hesMs,
        errorType: quality < 3 ? 'vocabulary' : null
      },
    })
  } catch (e) { /* 非关键 */ }
}

async function handleQuality(quality) {
  if (!currentWord.value) return
  const hesMs = cardStartTime.value ? Date.now() - cardStartTime.value : 0
  hesitationMs.value = hesMs
  const w = currentWord.value

  if (quality >= QUALITY_KNOWN) {
    correctList.value.push(w)
  } else {
    wrongSet.value.add(w.id)
    if (authStore.isLoggedIn && !favoriteStore.isFavorite(w.id)) {
      try {
        await favoriteStore.addFavorite(w.id, w.langCode || currentLang.value)
        autoAddedFav.value.add(w.id)
      } catch (e) { /* 静默 */ }
    }
  }

  await reportToBackend(w.id, quality, hesMs)
  advance()
}

function advance() {
  if (index.value >= words.value.length - 1) { step.value = 'practice'; return }
  index.value++
  resetCardState()
}

// ====== 巩固练习（学习-练习闭环） ======
const practiceResult = ref(null)

function onPracticeDone(result) {
  practiceResult.value = result
  step.value = 'finished'
}

function skipPractice() {
  practiceResult.value = { score: 0, total: 0, maxCombo: 0, wrongIds: [] }
  step.value = 'finished'
}

function checkSpell() {
  const input = spellInput.value.trim()
  if (!input) return
  const isCorrect = input.toLowerCase() === currentWord.value.word.toLowerCase()
  spellFeedback.value = isCorrect
  if (isCorrect) {
    const hesMs = cardStartTime.value ? Date.now() - cardStartTime.value : 0
    const quality = hesToQuality(hesMs, true)
    setTimeout(() => {
      correctList.value.push(currentWord.value)
      reportToBackend(currentWord.value.id, quality, hesMs)
      advance()
    }, 600)
  }
}

async function markSpellWrongNext() {
  if (!currentWord.value) return
  const hesMs = cardStartTime.value ? Date.now() - cardStartTime.value : 0
  wrongSet.value.add(currentWord.value.id)
  await reportToBackend(currentWord.value.id, QUALITY_FORGET, hesMs)
  if (authStore.isLoggedIn && !favoriteStore.isFavorite(currentWord.value.id)) {
    try {
      await favoriteStore.addFavorite(currentWord.value.id, currentWord.value.langCode || currentLang.value)
      autoAddedFav.value.add(currentWord.value.id)
    } catch (e) { /* 静默 */ }
  }
  spellFeedback.value = null
  advance()
}

function restart() {
  if (wrongOnly.value) { start(); return }
  step.value = 'setup'; flipReset()
}

function startWeakOnly() {
  studyMode.value = 'weak'
  poolSize.value = Math.min(wrongSet.size, 20) || 10
  start()
}

function onKeydown(e) {
  if (step.value !== 'studying') return
  if (mode.value === 'card' && flipped.value) {
    if (e.key === '1') handleQuality(QUALITY_FORGET)
    else if (e.key === '2') handleQuality(QUALITY_VAGUE)
    else if (e.key === '3') handleQuality(QUALITY_KNOWN)
    else if (e.key === '4') handleQuality(QUALITY_MASTERED)
    else if (e.key === ' ') { e.preventDefault(); flip() }
    else if (e.key === 's' || e.key === 'S') { speakWord() }
  } else if (mode.value === 'card' && !flipped.value) {
    if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); flip() }
    else if (e.key === 's' || e.key === 'S') { speakWord() }
  } else if (mode.value === 'listen' && listenAnswer.value === null) {
    const idx = ['1','2','3','4'].indexOf(e.key)
    if (idx >= 0 && idx < listenOptions.value.length) { e.preventDefault(); handleListenAnswer(listenOptions.value[idx]) }
    else if (e.key === 's' || e.key === 'S') { speakWord() }
  } else if (mode.value === 'spell' && e.key === 'Enter') {
    if (spellFeedback.value === false) markSpellWrongNext()
    else checkSpell()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  stopSpeaking()
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle :text="(LANG_NAMES[currentLang] || '') + ' 背单词'" tag="h1" />
      <p class="page-sub">{{ currentLevelLabel }}</p>
    </div>

    <!-- ====== 设置阶段 ====== -->
    <div v-if="step === 'setup'" class="setup-card">
      <div v-if="authStore.isLoggedIn" class="overview-strip">
        <div class="ov-item"><span class="ov-val">{{ recentStats.totalWords || 0 }}</span><span class="ov-lbl">学习词汇</span></div>
        <div class="ov-div" />
        <div class="ov-item"><span class="ov-val mastered">{{ recentStats.masteredWords || 0 }}</span><span class="ov-lbl">已掌握</span></div>
        <div class="ov-div" />
        <div class="ov-item"><span class="ov-val">{{ recentStats.masteryRate || 0 }}%</span><span class="ov-lbl">掌握率</span></div>
        <div class="ov-div" />
        <div class="ov-item"><span class="ov-val wrong" :class="{ active: wrongOnly }">{{ recentStats.wrongCount || 0 }}</span><span class="ov-lbl">错题</span></div>
      </div>

      <div v-if="authStore.isLoggedIn" class="wrongbook-entry">
        <div class="wb-info">
          <span class="wb-emoji icon-svg notebook" />
          <span class="wb-text">
            <b>错题本</b>
            <i>{{ wrongOnly ? '专注消灭已标记的薄弱词汇' : (recentStats.wrongCount ? `${recentStats.wrongCount} 个待消灭` : '暂无错题记录') }}</i>
          </span>
        </div>
        <button class="btn btn-secondary btn-sm" @click="toggleWrongOnly">
          {{ wrongOnly ? '退出专项' : '开始专项练习' }}
        </button>
      </div>
      <div v-if="wrongOnly" class="review-reminder wrongonly-tip">
        <span class="icon-svg target" /> 错题专项模式：仅练习历史错词，答对会逐渐清除错误标记
      </div>

      <div v-if="todayReviewCount > 0 && authStore.isLoggedIn" class="review-reminder">
        <span class="icon-svg book" /> 今日待复习 {{ todayReviewCount }} 个单词
      </div>

      <div class="setup-section">
        <p class="setup-section-title">学习方式</p>
        <div class="mode-row">
          <button class="btn" :class="mode === 'card' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="mode = 'card'">卡片</button>
          <button class="btn" :class="mode === 'listen' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="mode = 'listen'">听音</button>
          <button class="btn" :class="mode === 'spell' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="mode = 'spell'">拼写</button>
        </div>
      </div>

      <div class="setup-section">
        <p class="setup-section-title">学习策略</p>
        <div class="study-mode-row">
          <button
            v-for="(label, key) in studyModeLabels"
            :key="key"
            class="btn"
            :class="studyMode === key ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'"
            @click="studyMode = key"
          >{{ label }}</button>
        </div>
      </div>

      <div class="setup-section">
        <p class="setup-section-title">本次数量</p>
        <div class="setup-row">
          <select v-model="poolSize">
            <option :value="10">10</option><option :value="20">20</option><option :value="30">30</option><option :value="50">50</option>
          </select>
          <span class="unit">个单词</span>
        </div>
      </div>

      <div class="setup-section">
        <p class="setup-section-title">词汇等级</p>
        <div class="level-row">
          <button class="btn" :class="currentLevel === -1 ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="currentLevel = -1">全部</button>
          <button v-for="lv in examLevels" :key="lv.value" class="btn" :class="currentLevel === lv.value ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="currentLevel = lv.value">{{ lv.examLabel }}</button>
        </div>
      </div>

      <button class="btn btn-primary btn-lg" :disabled="loading" @click="start">
        {{ loading ? '加载中...' : '开始背诵' }}
      </button>

      <div class="quick-links">
        <router-link to="/vocabulary">词汇库</router-link>
        <router-link to="/favorites">收藏本</router-link>
        <router-link to="/stats">学习分析</router-link>
      </div>
    </div>

    <!-- ====== 背诵阶段 ====== -->
    <div v-if="step === 'studying' && currentWord" class="study-area">
      <div class="study-card">
        <div class="study-header">
          <div class="sh-left">
            <span class="sh-count">{{ index + 1 }}<i>/{{ words.length }}</i></span>
            <span class="sh-progress-text">{{ wrongOnly ? '错题专练 · ' : '' }}第 {{ index + 1 }} 个单词</span>
          </div>
          <div class="sh-right">
            <span class="acc-badge" :class="{ high: accuracy >= 80, mid: accuracy >= 50 }">正确率 {{ accuracy }}%</span>
            <span class="mastered-badge">✓ {{ correctList.length }} 已掌握</span>
          </div>
        </div>
        <div class="progress-bar"><div class="progress-fill" :style="{ width: progress + '%' }" /></div>

        <!-- ====== 卡片模式 ====== -->
        <template v-if="mode === 'card'">
          <div class="flashcard" :class="{ flipped }" @click="flip">
            <div class="card-inner">
              <div class="card-face front">
                <button class="btn btn-icon btn-ghost" @click.stop="speakWord" :class="{ speaking }"><span class="icon-svg speaker" /></button>
                <span class="card-word">{{ currentWord.word }}</span>
                <span v-if="currentWord.phonetic" class="card-phonetic">{{ currentWord.phonetic }}</span>
                <span class="card-hint">点击或按空格翻面</span>
              </div>
              <div class="card-face back">
                <p class="card-def">{{ currentWord.definition }}</p>
                <span v-if="currentWord.partOfSpeech" class="card-pos">{{ currentWord.partOfSpeech }}</span>
                <p v-if="currentWord.exampleSentence" class="card-ex">{{ currentWord.exampleSentence }}</p>
                <button v-if="currentWord.exampleSentence" class="btn btn-icon btn-sm btn-ghost" @click.stop="speakExample"><span class="icon-svg speaker" /></button>
                <p v-if="currentWord.exampleTranslation" class="card-ex-cn">{{ currentWord.exampleTranslation }}</p>
              </div>
            </div>
          </div>
          <div v-if="flipped" class="quality-panel">
            <p class="quality-title">这个单词你掌握得怎么样？</p>
            <div class="quality-row">
              <button class="btn quality-btn forget" @click="handleQuality(QUALITY_FORGET)"><b>忘记</b><i>1</i></button>
              <button class="btn quality-btn vague" @click="handleQuality(QUALITY_VAGUE)"><b>模糊</b><i>2</i></button>
              <button class="btn quality-btn known" @click="handleQuality(QUALITY_KNOWN)"><b>认识</b><i>3</i></button>
              <button class="btn quality-btn mastered" @click="handleQuality(QUALITY_MASTERED)"><b>掌握</b><i>4</i></button>
            </div>
            <p class="kb-hint">空格 翻面 · S 发音 · 数字键自评</p>
          </div>
        </template>

        <!-- ====== 听音辨义模式 ====== -->
        <template v-if="mode === 'listen'">
          <div class="listen-card">
            <button class="btn btn-icon btn-ghost" @click="speakWord" :class="{ speaking }"><span class="icon-svg speaker" /></button>
            <p class="listen-tip">听发音，选出对应的释义</p>
            <div class="listen-options">
              <button
                v-for="(opt, i) in listenOptions"
                :key="i"
                class="btn listen-opt"
                :class="{
                  'listen-correct': listenAnswer !== null && opt === currentWord.definition,
                  'listen-wrong': listenAnswer === opt && listenAnswer !== currentWord.definition
                }"
                :disabled="listenAnswer !== null"
                @click="handleListenAnswer(opt)"
              >
                <span class="choice-num">{{ i + 1 }}</span>
                <span class="choice-text">{{ opt }}</span>
              </button>
            </div>
            <div v-if="listenAnswer !== null && listenCorrect === false" class="spell-err">
              正确答案：<strong>{{ currentWord.definition }}</strong>
              <button class="btn btn-secondary btn-sm" @click="listenWrongNext">下一题</button>
            </div>
            <p class="kb-hint">键盘：1-4 选择 | S 重听</p>
          </div>
        </template>

        <!-- ====== 拼写模式 ====== -->
        <template v-if="mode === 'spell'">
          <div class="spell-card">
            <button class="btn btn-icon btn-ghost" @click="speakWord" :class="{ speaking }"><span class="icon-svg speaker" /></button>
            <p class="spell-prompt">{{ currentWord.definition }}</p>
            <p v-if="currentWord.partOfSpeech" class="spell-pos">{{ currentWord.partOfSpeech }}</p>
            <div class="spell-row">
              <input v-model="spellInput" class="spell-input" placeholder="输入单词..."
                :disabled="spellFeedback !== null" @keyup.enter="checkSpell" />
              <button v-if="spellFeedback === null" class="btn btn-primary btn-sm" @click="checkSpell">✓</button>
            </div>
            <div v-if="spellFeedback === true" class="spell-ok"> 正确！</div>
            <div v-if="spellFeedback === false" class="spell-err">
               正确答案：<strong>{{ currentWord.word }}</strong>
              <button class="btn btn-secondary btn-sm" @click="markSpellWrongNext">下一题</button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- ====== 巩固练习阶段 ====== -->
    <div v-if="step === 'practice'" class="study-area">
      <PracticeSession :words="words" :lang-code="currentLang" @done="onPracticeDone" @skip="skipPractice" />
    </div>

    <!-- ====== 完成阶段 ====== -->
    <div v-if="step === 'finished'" class="finish-card">
      <div class="finish-emoji">
        <span v-if="accuracy === 100" class="fe-icon perfect">★</span>
        <span v-else-if="accuracy >= 70" class="fe-icon good">◆</span>
        <span v-else class="fe-icon keep">●</span>
      </div>
      <div class="finish-title">
        <template v-if="accuracy === 100 && practiceResult.total > 0 && practiceResult.score === practiceResult.total">全部掌握！</template>
        <template v-else-if="accuracy === 100">全部掌握！</template>
        <template v-else-if="accuracy >= 70">掌握良好</template>
        <template v-else>继续加油</template>
      </div>

      <div class="finish-stats">
        <div class="fs correct"><span class="fs-num">{{ correctList.length }}</span>已掌握</div>
        <div class="fs wrong"><span class="fs-num">{{ wrongSet.size }}</span>需复习</div>
        <div class="fs fav" v-if="autoAddedFav.size"><span class="fs-num">{{ autoAddedFav.size }}</span>已收藏</div>
      </div>

      <!-- 练习成绩 -->
      <div v-if="practiceResult && practiceResult.total > 0" class="practice-result">
        <div class="pr-head">
          <span class="pr-label">巩固练习</span>
          <span class="pr-score">{{ practiceResult.score }} / {{ practiceResult.total }}</span>
        </div>
        <div class="pr-meter">
          <div class="pr-fill" :style="{ width: (practiceResult.score / practiceResult.total * 100) + '%' }" />
        </div>
        <div class="pr-sub">
          <span v-if="practiceResult.maxCombo >= 3"><span class="icon-svg fire" /> 最高连击 {{ practiceResult.maxCombo }}</span>
          <span v-else>完成 {{ practiceResult.total }} 题</span>
          <span>练习答对 {{ practiceResult.score }} 题</span>
        </div>
      </div>

      <div v-if="wrongSet.size > 0" class="weak-list">
        <h4>薄弱词汇</h4>
        <div v-for="w in words.filter(w => wrongSet.has(w.id))" :key="w.id" class="weak-word">
          <strong>{{ w.word }}</strong> {{ w.definition }}
        </div>
      </div>

      <div class="finish-actions">
        <button class="btn btn-primary btn-lg" @click="restart">再来一轮</button>
        <button v-if="wrongSet.size > 0" class="btn btn-secondary" @click="startWeakOnly()">只练薄弱的</button>
      </div>
      <div class="finish-links">
        <router-link to="/favorites">收藏本复习</router-link>
        <router-link to="/stats">学习分析</router-link>
        <router-link to="/vocabulary">词汇库</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 640px; margin: 0 auto; padding-bottom: 60px; }
.page-header { text-align: center; padding: 20px 0 8px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); }
.page-sub { font-size: 14px; color: var(--color-text-muted); }

/* ====== 设置 ====== */
.setup-card {
  max-width: 480px; margin: 24px auto;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 24px 26px 28px; text-align: center;
}
.setup-section { padding: 14px 0; border-top: 1px solid rgba(0,0,0,0.05); }
.setup-section-title {
  font-size: 12px; font-weight: 600; color: #a5a5a5;
  text-transform: uppercase; letter-spacing: 0.6px;
  margin-bottom: 10px;
}

.overview-strip {
  display: flex; align-items: center; justify-content: center; gap: 0;
  background: rgba(255,255,255,0.72); backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: 14px;
  padding: 14px 8px; margin-bottom: 20px;
}
.ov-item { display: flex; flex-direction: column; align-items: center; padding: 0 18px; }
.ov-val { font-size: 20px; font-weight: 800; color: var(--color-text); }
.ov-val.mastered { color: #27ae60; }
.ov-val.wrong { color: #b0b0b0; transition: color 0.3s; }
.ov-val.wrong.active { color: #e74c3c; }
.ov-lbl { font-size: 11px; color: #999; }
.ov-div { width: 1px; height: 30px; background: rgba(0,0,0,0.06); }

/* ====== 错题本入口 ====== */
.wrongbook-entry {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  background: rgba(255,255,255,0.72); backdrop-filter: blur(12px);
  border: 1px dashed rgba(231,76,60,0.25); border-radius: 14px;
  padding: 12px 16px; margin-bottom: 14px;
}
.wb-info { display: flex; align-items: center; gap: 10px; }
.wb-emoji { font-size: 20px; }
.wb-text { display: flex; flex-direction: column; line-height: 1.35; text-align: left; }
.wb-text b { font-size: 14px; color: var(--color-text); }
.wb-text i { font-style: normal; font-size: 12px; color: #b0a9a0; }
.wrongonly-tip { border-color: rgba(231,76,60,0.25); background: linear-gradient(135deg, rgba(231,76,60,0.08), rgba(231,76,60,0.03)); }

.review-reminder {
  background: linear-gradient(135deg, rgba(240,151,92,0.1), rgba(240,151,92,0.05));
  border: 1px solid rgba(240,151,92,0.2);
  border-radius: 10px;
  padding: 10px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #d35400;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}
.review-reminder .icon-svg { flex-shrink: 0; }

.mode-row { display: flex; gap: 8px; justify-content: center; }
.mode-row button {
  padding: 8px 22px; border-radius: 20px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; color: #666; cursor: pointer; transition: all 0.2s;
}
.mode-row button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

.study-mode-row { display: flex; gap: 6px; justify-content: center; align-items: center; flex-wrap: wrap; }
.mode-label { font-size: 13px; color: #888; font-weight: 600; }
.study-mode-row button {
  padding: 5px 12px; border-radius: 16px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6);
  font-size: 12px; font-weight: 600; color: #666; cursor: pointer; transition: all 0.2s;
}
.study-mode-row button.active { border-color: #7c9db5; color: #7c9db5; background: rgba(124,157,181,0.08); }

.setup-row { display: flex; align-items: center; justify-content: center; gap: 10px; }
.setup-row label { font-size: 14px; font-weight: 600; color: var(--color-text); }
.setup-row select { padding: 8px 18px; border-radius: 12px; border: 1.5px solid #e0e0e0; background: #fafafa; font-size: 14px; }
.setup-row .unit { font-size: 13px; color: #888; }

.level-row { display: flex; gap: 6px; justify-content: center; flex-wrap: wrap; }
.level-row button {
  padding: 5px 14px; border-radius: 16px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6);
  font-size: 12px; font-weight: 600; color: #666; cursor: pointer; transition: all 0.2s;
}
.level-row button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.08); }

.start-btn {
  padding: 14px 48px; border-radius: 14px; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  font-size: 17px; font-weight: 700; cursor: pointer;
  box-shadow: 0 4px 18px rgba(90,125,150,0.25); transition: all 0.25s;
}
.start-btn:hover:not(:disabled) { transform: translateY(-2px); }
.start-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.quick-links { display: flex; gap: 16px; justify-content: center; margin-top: 20px; }
.quick-links a { font-size: 13px; color: #5a7d96; text-decoration: none; font-weight: 500; }
.quick-links a:hover { text-decoration: underline; }

/* ====== 背诵 ====== */
.study-area { max-width: 540px; margin: 20px auto; }
.study-card {
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 22px 24px 26px;
}
.study-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.sh-left { display: flex; align-items: baseline; gap: 10px; }
.sh-count { font-size: 20px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.sh-count i { font-style: normal; font-size: 13px; color: #b0b0b0; font-weight: 600; }
.sh-progress-text { font-size: 13px; color: var(--color-text-muted); }
.sh-right { display: flex; align-items: center; gap: 8px; }
.acc-badge { padding: 3px 12px; border-radius: 12px; font-weight: 600; font-size: 12px; }
.acc-badge.high { background: rgba(39,174,96,0.12); color: #27ae60; }
.acc-badge.mid { background: rgba(240,151,92,0.12); color: #f0975c; }
.acc-badge:not(.high):not(.mid) { background: rgba(231,76,60,0.1); color: #e74c3c; }
.mastered-badge { padding: 3px 12px; border-radius: 12px; background: rgba(39,174,96,0.08); color: #27ae60; font-size: 12px; font-weight: 600; }
.progress-bar { height: 6px; border-radius: 3px; background: rgba(0,0,0,0.06); margin-bottom: 20px; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #7c9db5, #5a7d96); transition: width 0.3s; }

/* 卡片 */
.flashcard { perspective: 800px; cursor: pointer; margin-bottom: 16px; }
.card-inner { position: relative; width: 100%; min-height: 300px; transform-style: preserve-3d; transition: transform 0.5s; border-radius: 18px; }
.flipped .card-inner { transform: rotateY(180deg); }
.card-face {
  position: absolute; inset: 0; backface-visibility: hidden;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 30px 26px; border-radius: 18px;
  background: rgba(250, 249, 246, 0.9); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); box-shadow: 0 8px 32px rgba(0,0,0,0.06);
}
.card-face.back { transform: rotateY(180deg); }

.speak-btn {
  position: absolute; top: 16px; right: 16px;
  width: 36px; height: 36px; border-radius: 50%;
  border: 1.5px solid #e0e0e0; background: rgba(255,255,255,0.8);
  font-size: 16px; cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center;
}
.speak-btn:hover { border-color: #7c9db5; background: rgba(124,157,181,0.08); }
.speak-btn.speaking { border-color: #27ae60; background: rgba(39,174,96,0.1); animation: pulse 1s infinite; }

.speak-btn-sm {
  width: 28px; height: 28px; border-radius: 50%;
  border: 1px solid #ddd; background: rgba(255,255,255,0.6);
  font-size: 12px; cursor: pointer; margin-top: 4px;
}
.speak-btn-sm:hover { border-color: #7c9db5; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.card-word { font-size: 38px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.card-phonetic { font-size: 15px; color: #999; margin-top: 10px; }
.card-hint { font-size: 12px; color: #c5c5c5; margin-top: 16px; }
.card-def { font-size: 19px; color: #444; text-align: center; line-height: 1.6; }
.card-pos { font-size: 12px; padding: 2px 10px; border-radius: 8px; background: rgba(124,92,191,0.08); color: #7c5cbf; margin-top: 8px; }
.card-ex { font-size: 14px; color: #666; margin-top: 12px; text-align: center; }
.card-ex-cn { font-size: 13px; color: #aaa; margin-top: 4px; }

.action-row { display: flex; gap: 14px; justify-content: center; }
.btn {
  padding: 14px 28px; border-radius: 14px; border: none; font-size: 16px; font-weight: 700; cursor: pointer; transition: all 0.25s;
}
.btn.wrong { background: linear-gradient(135deg, #e74c3c, #c0392b); color: #fff; flex: 1; }
.btn.wrong:hover { transform: translateY(-2px); box-shadow: 0 4px 14px rgba(231,76,60,0.3); }
.btn.correct { background: linear-gradient(135deg, #27ae60, #1e8449); color: #fff; flex: 2; }
.btn.correct:hover { transform: translateY(-2px); box-shadow: 0 4px 14px rgba(39,174,96,0.3); }

/* SRS 自评面板 */
.quality-panel {
  border-top: 1px dashed rgba(0,0,0,0.08);
  padding-top: 16px;
  animation: quality-in 0.25s ease;
}
@keyframes quality-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.quality-title { text-align: center; font-size: 13px; color: #999; margin-bottom: 12px; font-weight: 500; }
.quality-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.quality-btn {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 12px 6px; border-radius: 14px; font-size: 14px; font-weight: 600; color: #fff;
  border: none; cursor: pointer; transition: all 0.2s;
}
.quality-btn b { font-size: 14px; font-weight: 700; }
.quality-btn i {
  font-style: normal; font-size: 11px; font-weight: 400; opacity: 0.75;
  background: rgba(255,255,255,0.2); border-radius: 8px; padding: 0 7px; line-height: 16px;
}
.quality-btn:hover { transform: translateY(-3px); box-shadow: 0 6px 16px rgba(0,0,0,0.16); }
.quality-btn.forget { background: linear-gradient(160deg, #e76f6f, #c0392b); }
.quality-btn.vague { background: linear-gradient(160deg, #f2a86e, #d35400); }
.quality-btn.known { background: linear-gradient(160deg, #7d97ad, #3e5a6e); }
.quality-btn.mastered { background: linear-gradient(160deg, #43c17d, #1e8449); }
.kb-hint { text-align: center; font-size: 12px; color: #c0c0c0; margin-top: 12px; }

/* 听音辨义 */
.listen-card { text-align: center; padding: 30px 20px; }
.listen-card .speak-btn { position: static; margin-bottom: 10px; }
.listen-tip { font-size: 14px; color: #999; margin-bottom: 22px; }
.listen-options { display: flex; flex-direction: column; gap: 10px; max-width: 420px; margin: 0 auto; }
.listen-opt {
  display: flex; align-items: center; gap: 12px; text-align: left;
  padding: 14px 18px; border-radius: 12px;
  border: 1.5px solid #e8e8e8; background: #fafafa;
  color: var(--color-text); font-size: 15px; cursor: pointer; transition: all 0.2s;
}
.listen-opt:hover:not(:disabled) { border-color: #7c9db5; background: rgba(124,157,181,0.06); }
.listen-opt.listen-correct { border-color: #27ae60; background: #eefaf3; color: #1e8449; font-weight: 700; }
.listen-opt.listen-wrong { border-color: #e74c3c; background: #fef0ef; color: #c0392b; font-weight: 700; }
.listen-opt:disabled { cursor: default; }

/* 拼写 */
.spell-card { text-align: center; padding: 40px 20px; }
.spell-card .speak-btn { position: static; margin-bottom: 12px; }
.spell-prompt { font-size: 20px; font-weight: 700; color: var(--color-text); margin-bottom: 6px; }
.spell-pos { font-size: 13px; color: #999; margin-bottom: 20px; }
.spell-row { display: flex; gap: 8px; justify-content: center; }
.spell-input {
  padding: 14px 18px; border-radius: 12px; border: 1.5px solid #e0e0e0;
  background: #fafafa; font-size: 20px; text-align: center; width: 260px; outline: none;
}
.spell-input:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.1); }
.check-btn {
  width: 48px; border-radius: 12px; border: none; background: #5a7d96; color: #fff;
  font-size: 20px; cursor: pointer;
}
.check-btn:hover { background: #4a6d86; }
.spell-ok { font-size: 18px; font-weight: 700; color: #27ae60; margin-top: 16px; }
.spell-err { font-size: 15px; color: #e74c3c; margin-top: 16px; }
.spell-next { margin-left: 10px; padding: 4px 14px; border-radius: 8px; border: 1px solid #ccc; background: #fff; color: #666; cursor: pointer; font-size: 13px; }

/* ====== 完成 ====== */
.finish-card {
  max-width: 500px; margin: 24px auto; text-align: center;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 32px 30px;
}
.finish-emoji { font-size: 40px; margin-bottom: 8px; }
.fe-icon {
  display: inline-block; font-size: 36px; line-height: 1;
}
.fe-icon.perfect { color: #f0c040; }
.fe-icon.good { color: #5a7d96; }
.fe-icon.keep { color: #e74c3c; }
.finish-title { font-size: 24px; font-weight: 800; color: var(--color-text); margin-bottom: 20px; }
.finish-stats { display: flex; gap: 14px; justify-content: center; margin-bottom: 20px; }
.fs {
  padding: 14px 22px; border-radius: 14px; background: rgba(255,255,255,0.8);
  border: 1px solid rgba(0,0,0,0.05); min-width: 90px; font-size: 13px; color: #888;
}
.fs.correct { border-color: rgba(39,174,96,0.2); }
.fs.wrong { border-color: rgba(231,76,60,0.2); }
.fs.fav { border-color: rgba(240,151,92,0.2); }
.fs-num { font-size: 28px; font-weight: 800; display: block; }
.fs.correct .fs-num { color: #27ae60; }
.fs.wrong .fs-num { color: #e74c3c; }
.fs.fav .fs-num { color: #f0975c; }

.weak-list { text-align: left; margin-bottom: 20px; }
.weak-list h4 { font-size: 15px; font-weight: 700; color: var(--color-text); margin-bottom: 8px; }
.weak-word { padding: 8px 14px; margin-bottom: 4px; border-radius: 8px; background: #fef5f5; font-size: 14px; color: #555; }

/* 巩固练习成绩 */
.practice-result {
  background: rgba(90,125,150,0.06);
  border: 1px solid rgba(90,125,150,0.18);
  border-radius: 14px;
  padding: 14px 18px;
  margin-bottom: 18px;
}
.pr-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.pr-label { font-size: 13px; font-weight: 700; color: #5a7d96; }
.pr-score { font-size: 18px; font-weight: 800; color: #5a7d96; }
.pr-meter { height: 8px; border-radius: 4px; background: rgba(0,0,0,0.07); overflow: hidden; }
.pr-fill { height: 100%; border-radius: 4px; background: linear-gradient(90deg, #7c9db5, #5a7d96); transition: width 0.6s ease; }
.pr-sub { display: flex; justify-content: space-between; margin-top: 8px; font-size: 12px; color: #888; }

.finish-actions { display: flex; gap: 10px; justify-content: center; margin-bottom: 18px; }
.big-btn {
  padding: 12px 32px; border-radius: 12px; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  font-size: 15px; font-weight: 700; cursor: pointer; transition: all 0.25s;
}
.big-btn:hover { transform: translateY(-2px); }
.outline-btn {
  padding: 12px 24px; border-radius: 12px; border: 1.5px solid #e74c3c;
  background: #fff; color: #e74c3c; font-size: 14px; font-weight: 600; cursor: pointer;
}
.outline-btn:hover { background: #fef5f5; }

.finish-links { display: flex; gap: 18px; justify-content: center; }
.finish-links a { font-size: 13px; color: #5a7d96; text-decoration: none; font-weight: 500; }
.finish-links a:hover { text-decoration: underline; }
</style>
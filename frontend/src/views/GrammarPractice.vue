<script setup>
/**
 * GrammarPractice.vue — 语法练习独立页面
 * 复用 grammar store 统一管理状态，从后端加载练习题
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGrammarStore } from '@/stores/grammar'
import { getExamLevels, getLevelLabel } from '@/data/examLevels'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const authStore = useAuthStore()
const store = useGrammarStore()

const currentLang = ref(authStore.targetLanguage || 'en')
const mode = ref('learn')

watch(() => authStore.targetLanguage, (newLang) => {
  currentLang.value = newLang || 'en'
  store.setLang(currentLang.value)
})

const langNames = { en: '英语', ja: '日语', ko: '韩语', fr: '法语', de: '德语' }
const langSubtitles = {
  en: '时态·介词·从句·长难句',
  ja: '助词·活用·敬语·长难句',
  ko: '助词·语尾·敬语·长难句',
  fr: '变位·时态·形容词·长难句',
  de: '格·词序·动词·长难句',
}

// ===== 语法教程 =====
async function loadLessons() {
  await store.fetchLessons()
}

// ===== 分级练习 =====
const currentLevel = ref(0)
const levelLabels = [
  { en: 'Beginner', ja: '初級', ko: '초급', fr: 'Débutant', de: 'Anfänger' },
  { en: 'Intermediate', ja: '中級', ko: '중급', fr: 'Intermédiaire', de: 'Mittelstufe' },
  { en: 'Advanced', ja: '上級', ko: '고급', fr: 'Avancé', de: 'Fortgeschritten' },
]

const currentLevelLabel = computed(() => {
  return getLevelLabel(currentLang.value, authStore.targetLevel)
})

const fills = computed(() => store.currentPractices.filter(p => p.type === 'fill'))
const corrects = computed(() => store.currentPractices.filter(p => p.type === 'correct'))

function switchLevel(index) {
  currentLevel.value = index
  store.fetchPractices(index)
}

function handleCheck(q) {
  store.checkAnswer(q, q.userAnswer)
}

function resetLevel() {
  store.resetPractices()
}

const totalDone = computed(() => store.answeredCount)
const totalQuestions = computed(() => store.totalQuestions)
const totalCorrect = computed(() => store.correctCount)

// ===== 长难句 =====
const expandedSid = ref(null)

function toggleSentence(id) {
  const sid = String(id)
  expandedSid.value = expandedSid.value === sid ? null : sid
}

function parseGrammar(raw) {
  if (!raw) return []
  try { return JSON.parse(raw) }
  catch { return typeof raw === 'string' ? raw.replace(/[\[\]"]/g, '').split(',').map(s => s.trim()).filter(Boolean) : [] }
}

function getGrammarPoints(s) { return s.grammarPoints || s.grammar_points || null }
function getAnalysis(s) { return s.analysis || '' }
function getTranslation(s) { return s.translation || '' }

function switchMode(m) {
  mode.value = m
  if (m === 'learn') loadLessons()
  if (m === 'practice') {
    const lv = authStore.targetLevel
    store.fetchPractices(lv !== null && lv !== -1 ? lv : 0)
  }
  if (m === 'sentences') store.fetchSentences()
}

onMounted(() => {
  loadLessons()
  store.fetchSentences()
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle :text="(langNames[currentLang] || '') + ' 语法中心'" tag="h1" />
      <p class="page-sub">{{ langSubtitles[currentLang] || '' }}</p>
    </div>

    <div class="tab-bar">
      <button :class="{ active: mode === 'learn' }" @click="switchMode('learn')">语法教程</button>
      <button :class="{ active: mode === 'practice' }" @click="switchMode('practice')">分级练习</button>
      <button :class="{ active: mode === 'sentences' }" @click="switchMode('sentences')">长难句</button>
    </div>

    <!-- 语法教程 -->
    <div v-if="mode === 'learn'">
      <LoadingSpinner v-if="store.loading.lessons" />
      <div v-else-if="store.lessons.length > 0" class="learn-area">
        <div v-for="l in store.lessons" :key="l.id" class="lesson-card">
          <div class="lesson-header" @click="l.expanded = !l.expanded">
            <span>{{ l.title }}</span>
            <span class="expand-icon">{{ l.expanded ? '▾' : '▸' }}</span>
          </div>
          <div v-if="l.expanded" class="lesson-body">
            <div v-for="(s, i) in l.sections" :key="i" class="lesson-section">
              <h4 class="lesson-subtitle">{{ s.subtitle }}</h4>
              <p class="lesson-text">{{ s.content }}</p>
            </div>
            <div v-if="l.video_url" class="lesson-video">
              <a :href="l.video_url" target="_blank" rel="noopener" class="video-link">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                观看 B 站教学视频
              </a>
            </div>
          </div>
        </div>
      </div>
      <p v-else class="empty-text">该语言暂无语法教程</p>
    </div>

    <!-- 分级练习 -->
    <div v-if="mode === 'practice'" class="practice-area">
      <div class="level-bar">
        <span class="level-label">{{ currentLevelLabel }}</span>
        <button class="level-refresh" @click="store.fetchPractices(authStore.targetLevel)">刷新</button>
      </div>
      <div class="progress-info" v-if="totalQuestions">已完成 {{ totalDone }}/{{ totalQuestions }}，正确 {{ totalCorrect }}</div>

      <LoadingSpinner v-if="store.loading.practices || store.aiLoading" />

      <div v-else-if="!fills.length && !corrects.length" class="empty-practice">
        <p>该等级暂无语法练习题</p>
        <button class="ai-gen-btn" @click="store.generateAIQuestions(currentLevel)">
          <span v-if="store.aiLoading">AI 出题中...</span>
          <span v-else>AI 生成新题目</span>
        </button>
      </div>

      <div v-if="store.hasAIPractices" class="ai-badge">
        <span>AI 生成题目 · 练习后不记录成绩</span>
        <button class="ai-clear-btn" @click="store.clearAIPractices()">清除</button>
      </div>

      <div v-if="fills.length" class="question-block">
        <h3 class="block-title">填空题</h3>
        <div v-for="q in fills" :key="q.id" class="q-item"
          :class="{
            done: store.practiceAnswers[q.id] !== undefined,
            correct: store.practiceAnswers[q.id]?.isCorrect,
            wrong: store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect
          }">
          <p class="q-prompt">{{ q.question }}</p>
          <p v-if="q.hint" class="q-hint">{{ q.hint }}</p>
          <div class="q-row">
            <input v-model="q.userAnswer" class="q-input" placeholder="输入答案..."
              :disabled="store.practiceAnswers[q.id] !== undefined"
              @keyup.enter="handleCheck(q)" />
            <button v-if="store.practiceAnswers[q.id] === undefined" class="check-btn" @click="handleCheck(q)">✓</button>
          </div>
          <p v-if="store.practiceAnswers[q.id]?.isCorrect" class="feedback-ok">正确!</p>
          <div v-if="store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect" class="feedback-bad">
            错误! 正确答案: <strong>{{ q.answer }}</strong>
            <span v-if="q.explanation" class="explanation"> — {{ q.explanation }}</span>
          </div>
        </div>
      </div>

      <div v-if="corrects.length" class="question-block">
        <h3 class="block-title">纠错题</h3>
        <p class="block-hint">找出句子中的错误并修正</p>
        <div v-for="q in corrects" :key="q.id" class="q-item"
          :class="{
            done: store.practiceAnswers[q.id] !== undefined,
            correct: store.practiceAnswers[q.id]?.isCorrect,
            wrong: store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect
          }">
          <p class="q-sentence">{{ q.question }}</p>
          <div class="q-row">
            <input v-model="q.userAnswer" class="q-input" placeholder="输入正确形式..."
              :disabled="store.practiceAnswers[q.id] !== undefined"
              @keyup.enter="handleCheck(q)" />
            <button v-if="store.practiceAnswers[q.id] === undefined" class="check-btn" @click="handleCheck(q)">✓</button>
          </div>
          <p v-if="store.practiceAnswers[q.id]?.isCorrect" class="feedback-ok">正确!</p>
          <div v-if="store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect" class="feedback-bad">
            正确答案: <strong>{{ q.answer }}</strong>
            <span v-if="q.explanation" class="explanation"> — {{ q.explanation }}</span>
          </div>
        </div>
      </div>
      <button class="reset-btn" @click="resetLevel()">重做本组</button>
    </div>

    <!-- 长难句精析 -->
    <div v-if="mode === 'sentences'">
      <div v-if="store.dailySentence" class="daily-card">
        <div class="daily-badge">每日一句 · {{ store.dailySentence.level === 'Advanced' ? '高级' : store.dailySentence.level === 'Intermediate' ? '中级' : store.dailySentence.level || '' }}</div>
        <p class="daily-sentence">{{ store.dailySentence.sentence }}</p>
        <p class="daily-translation">{{ getTranslation(store.dailySentence) }}</p>
        <div v-if="store.dailySentence.source" class="daily-source">— {{ store.dailySentence.source }}</div>
        <div class="daily-tags">
          <span v-for="(g, i) in parseGrammar(getGrammarPoints(store.dailySentence))" :key="i" class="grammar-tag">{{ g }}</span>
        </div>
        <details class="daily-analysis">
          <summary>查看结构分析</summary>
          <pre class="analysis-text">{{ getAnalysis(store.dailySentence) }}</pre>
        </details>
      </div>

      <div class="filter-bar">
        <button :class="{ active: store.sentenceLevel === 'Intermediate' }" @click="store.setSentenceLevel('Intermediate')">中级</button>
        <button :class="{ active: store.sentenceLevel === 'Advanced' }" @click="store.setSentenceLevel('Advanced')">高级</button>
      </div>

      <div class="sentence-list">
        <div v-for="s in store.sentenceList" :key="s.id" class="sentence-card"
          :class="{ expanded: expandedSid === String(s.id) }">
          <div class="sentence-header" @click="toggleSentence(String(s.id))">
            <div class="sentence-top-row">
              <span class="sentence-level-tag">{{ s.level === 'Advanced' ? '高级' : '中级' }}</span>
              <span v-if="s.source" class="sentence-source">{{ s.source }}</span>
              <span class="expand-arrow">{{ expandedSid === String(s.id) ? '▾' : '▸' }}</span>
            </div>
            <p class="sentence-text">{{ s.sentence }}</p>
          </div>
          <div v-if="expandedSid === String(s.id)" class="sentence-body">
            <div class="sentence-section">
              <h4>中文翻译</h4>
              <p>{{ getTranslation(s) }}</p>
            </div>
            <div class="sentence-section">
              <h4>语法点</h4>
              <div class="grammar-tags">
                <span v-for="(g, i) in parseGrammar(getGrammarPoints(s))" :key="i" class="grammar-tag">{{ g }}</span>
              </div>
            </div>
            <div class="sentence-section">
              <h4>句子结构分析</h4>
              <pre class="analysis-text">{{ getAnalysis(s) }}</pre>
            </div>
          </div>
        </div>
        <p v-if="store.sentenceList.length === 0" class="empty-text">暂无长难句数据</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 800px; margin: 0 auto; padding: 0 16px 40px; }
.page-header { text-align: center; padding: 20px 0 10px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--color-text-muted); }
.empty-text { text-align: center; color: var(--color-text-muted); padding: 60px 0; font-size: 14px; }

.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 20px 0; }
.tab-bar button { padding: 10px 24px; border-radius: 20px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s; color: #666; }
.tab-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

.learn-area { display: flex; flex-direction: column; gap: 14px; }
.lesson-card { background: rgba(255,255,255,0.8); backdrop-filter: blur(12px); border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg); overflow: hidden; }
.lesson-header { padding: 16px 22px; font-size: 17px; font-weight: 700; color: var(--color-text); cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.lesson-header:hover { background: rgba(0,0,0,0.02); }
.expand-icon { font-size: 14px; color: #aaa; }
.lesson-body { padding: 0 22px 20px; }
.lesson-section { margin-top: 16px; padding: 14px 18px; background: #f8fafb; border-radius: 10px; border: 1px solid #f0f0f0; }
.lesson-subtitle { font-size: 14px; font-weight: 700; color: #5a7d96; margin-bottom: 6px; }
.lesson-text { font-size: 14px; color: #555; line-height: 1.8; white-space: pre-wrap; }

.lesson-video { margin-top: 12px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.video-link {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px; background: rgba(251,114,153,0.06);
  border: 1px solid rgba(251,114,153,0.2); color: #fb7299;
  font-size: 13px; font-weight: 600; text-decoration: none;
  transition: all 0.2s;
}
.video-link:hover { background: #fb7299; color: #fff; border-color: #fb7299; }
.video-link svg { flex-shrink: 0; }

.level-bar {
  display: flex; gap: 8px; justify-content: center; align-items: center; flex-wrap: wrap; margin-bottom: 8px;
}
.level-label {
  font-size: 14px; font-weight: 700; color: #5a7d96;
  padding: 6px 16px; background: rgba(90,125,150,0.08); border-radius: 8px;
}
.level-refresh {
  padding: 6px 14px; border-radius: 8px; border: 1.5px solid #ddd;
  background: rgba(255,255,255,0.6); font-size: 12px; color: #666; cursor: pointer;
}
.level-refresh:hover { border-color: #5a7d96; color: #5a7d96; }
.progress-info { text-align: center; font-size: 13px; color: #5a7d96; font-weight: 600; margin-bottom: 18px; }

.question-block { margin-bottom: 24px; }
.block-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin-bottom: 4px; }
.block-hint { font-size: 12px; color: #888; margin-bottom: 12px; }
.q-item { padding: 14px 18px; margin-bottom: 12px; border-radius: 12px; border: 1px solid #eee; background: #fafafa; transition: all 0.3s; }
.q-item.done.correct { border-color: #d4edda; background: #f6fdf7; }
.q-item.done.wrong { border-color: #f8d7da; background: #fef9f9; }
.q-prompt { font-size: 15px; color: var(--color-text); margin-bottom: 4px; font-weight: 500; }
.q-sentence { font-size: 15px; color: var(--color-text); margin-bottom: 8px; line-height: 1.6; }
.q-hint { font-size: 12px; color: #888; margin-bottom: 8px; }
.q-row { display: flex; gap: 8px; }
.q-input { flex: 1; padding: 9px 14px; border-radius: 10px; border: 1.5px solid #e0e0e0; background: #fff; font-size: 14px; color: var(--color-text); outline: none; }
.q-input:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.1); }
.q-input:disabled { background: #f5f5f5; color: #999; }
.check-btn { padding: 9px 16px; border-radius: 10px; border: none; background: #5a7d96; color: #fff; font-size: 16px; cursor: pointer; }
.check-btn:hover { background: #4a6d86; }
.feedback-ok { color: #27ae60; font-size: 13px; font-weight: 600; margin-top: 6px; }
.feedback-bad { color: #c0392b; font-size: 13px; margin-top: 6px; line-height: 1.5; }
.feedback-bad strong { color: #27ae60; }
.explanation { color: #888; font-size: 12px; margin-left: 4px; }
.reset-btn { display: block; margin: 20px auto; padding: 10px 28px; border-radius: 10px; border: 1.5px solid #ddd; background: #fff; color: #666; font-size: 14px; cursor: pointer; }
.reset-btn:hover { background: #f5f5f5; }

.daily-card { background: linear-gradient(135deg, rgba(124,157,181,0.08), rgba(90,125,150,0.04)); border: 1.5px solid rgba(124,157,181,0.2); border-radius: var(--radius-lg); padding: 24px 28px; margin-bottom: 24px; }
.daily-badge { font-size: 13px; font-weight: 700; color: #5a7d96; margin-bottom: 12px; }
.daily-sentence { font-size: 18px; font-weight: 600; color: var(--color-text); line-height: 1.8; margin-bottom: 10px; }
.daily-translation { font-size: 14px; color: #666; line-height: 1.7; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid rgba(124,157,181,0.3); }
.daily-source { font-size: 12px; color: #999; font-style: italic; margin-bottom: 8px; }
.daily-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.daily-analysis { margin-top: 12px; }
.daily-analysis summary { font-size: 13px; color: #5a7d96; cursor: pointer; font-weight: 600; }
.grammar-tag { display: inline-block; padding: 3px 10px; border-radius: var(--radius-full); background: rgba(90,125,150,0.08); color: #5a7d96; font-size: 11px; font-weight: 600; }

.filter-bar { display: flex; gap: 10px; justify-content: center; margin-bottom: 20px; }
.filter-bar button { padding: 8px 20px; border-radius: 20px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6); font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; color: #666; }
.filter-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }
.sentence-list { display: flex; flex-direction: column; gap: 12px; }
.sentence-card { background: rgba(255,255,255,0.8); backdrop-filter: blur(12px); border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg); overflow: hidden; }
.sentence-card.expanded { border-color: rgba(124,157,181,0.2); }
.sentence-header { padding: 16px 20px; cursor: pointer; }
.sentence-header:hover { background: rgba(0,0,0,0.01); }
.sentence-top-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.sentence-level-tag { font-size: 10px; padding: 2px 8px; border-radius: 10px; background: rgba(0,0,0,0.05); color: #888; font-weight: 600; }
.sentence-source { font-size: 11px; color: #bbb; font-style: italic; }
.expand-arrow { margin-left: auto; font-size: 14px; color: #aaa; }
.sentence-text { font-size: 15px; color: var(--color-text); line-height: 1.7; margin: 0; }
.sentence-body { padding: 0 20px 20px; }
.sentence-section { margin-bottom: 14px; }
.sentence-section h4 { font-size: 13px; font-weight: 700; color: #5a7d96; margin-bottom: 6px; }
.sentence-section p { font-size: 14px; color: #555; line-height: 1.7; }
.grammar-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.analysis-text { font-size: 13px; color: #555; line-height: 1.8; white-space: pre-wrap; background: #f8f9fa; padding: 12px 16px; border-radius: 8px; font-family: var(--font-body); }

.empty-practice { text-align: center; padding: 40px 0; color: var(--color-text-muted); }
.empty-practice p { margin-bottom: 16px; font-size: 14px; }
.ai-gen-btn {
  padding: 11px 28px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #9b59b6, #8e44ad); color: #fff;
  font-size: 15px; font-weight: 600; cursor: pointer;
  transition: all 0.25s;
}
.ai-gen-btn:hover { opacity: 0.92; transform: translateY(-1px); }
.ai-badge {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; background: rgba(155,89,182,0.06);
  border: 1px solid rgba(155,89,182,0.15); border-radius: 10px;
  margin-bottom: 16px; font-size: 13px; color: #8e44ad;
}
.ai-clear-btn {
  padding: 4px 12px; border-radius: 6px; border: 1px solid rgba(155,89,182,0.3);
  background: #fff; color: #8e44ad; font-size: 12px; cursor: pointer;
}
.ai-clear-btn:hover { background: rgba(155,89,182,0.08); }
</style>

<script setup>
/**
 * WritingPractice.vue — 写作训练
 *  Level 1 = 仿写 · Level 3 = 自由写作
 */
import { ref, watch, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { LANG_NAMES } from '@/config/languages'

const authStore = useAuthStore()
const BASE = API_BASE_URL

const currentLang = ref(authStore.targetLanguage || 'en')
watch(() => authStore.targetLanguage, (newLang) => {
  currentLang.value = newLang || 'en'
  loadPrompt(currentLevel.value)
})

const writingLevels = [
  { level: 1, label: '仿写', desc: '替换结构，保持语法', icon: 'pencil', color: '#7c9db5' },
  { level: 3, label: '自由写作', desc: 'AI 出题自由发挥', icon: 'sparkles', color: '#b07c4f' },
]

function mapTargetLevelToWriting(targetLevel) {
  if (targetLevel === null || targetLevel === -1) return 3
  return targetLevel <= 3 ? 1 : 3
}

const currentLevel = ref(1)
const prompt = ref(null)
const loading = ref(false)
const error = ref('')

// AI 评分
const aiScore = ref(null)
const scoreLoading = ref(false)

async function requestAIScore() {
  if (!submittedText.value.trim()) return
  scoreLoading.value = true
  try {
    const json = await fetchJson(`${BASE}/ai/score-writing`, {
      method: 'POST',
      body: { text: submittedText.value, langCode: currentLang.value, topic: prompt.value?.topic || '' },
    })
    if (json.code === 200 && json.data) { aiScore.value = json.data }
    else { error.value = json.msg || '评分失败' }
  } catch (e) { error.value = 'AI 服务暂不可用' }
  finally { scoreLoading.value = false }
}

// 写作历史
const writingHistory = ref([])
const whLoading = ref(false)
const showWritingHistory = ref(false)

function viewWritingDetail(w) {
  if (w.prompt_json) {
    try { prompt.value = JSON.parse(w.prompt_json) } catch (e) { prompt.value = { type: w.type, topic: w.topic, instruction: '' } }
  } else {
    prompt.value = { type: w.type || '', topic: w.topic || '', instruction: '' }
  }
  submittedText.value = w.submitted_text || ''
  submitted.value = true
  isViewingHistory.value = true
  currentLevel.value = w.level || 1
  aiScore.value = null
  if (w.score) {
    try {
      aiScore.value = JSON.parse(w.score_detail || '{}')
      aiScore.value.overall = w.score
    } catch {
      aiScore.value = { overall: w.score }
    }
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function loadWritingHistory() {
  const userId = authStore.user?.id
  if (!userId) return
  whLoading.value = true
  try {
    const json = await fetchJson(`${BASE}/history/writing?userId=${userId}&limit=20`)
    writingHistory.value = json.data || []
  } catch (e) { writingHistory.value = [] }
  finally { whLoading.value = false }
}

function toggleHistory() {
  showWritingHistory.value = !showWritingHistory.value
  if (showWritingHistory.value) loadWritingHistory()
}

// AI 生成
const aiTopicInput = ref('')
const aiGenerating = ref(false)

async function aiGeneratePrompt(level) {
  aiGenerating.value = true
  error.value = ''
  submitted.value = false
  submittedText.value = ''
  revisionCount.value = 0
  wordCount.value = 0
  pasteWarning.value = false
  isViewingHistory.value = false
  aiScore.value = null

  try {
    const backendLevel = level === 1 ? 1 : 3
    const json = await fetchJson(`${BASE}/ai/generate-writing-prompt`, {
      method: 'POST',
      body: { langCode: currentLang.value, level: backendLevel, topic: aiTopicInput.value.trim() },
    })
    if (json.code === 200 && json.data) { prompt.value = json.data }
    else { prompt.value = null; error.value = json.msg || 'AI 出题失败，请重试' }
  } catch (e) { error.value = '网络错误'; prompt.value = null }
  finally { aiGenerating.value = false }
}

const submittedText = ref('')
const revisionCount = ref(0)
const wordCount = ref(0)
const submitted = ref(false)
const pasteWarning = ref(false)
const submitLoading = ref(false)
const isViewingHistory = ref(false)
const currentHistoryId = ref(null)
const focusTime = ref(0)
let focusTimerId = null
onUnmounted(() => { if (focusTimerId) clearInterval(focusTimerId) })

async function loadPrompt(level) {
  currentLevel.value = level
  loading.value = true
  error.value = ''
  submitted.value = false
  submittedText.value = ''
  revisionCount.value = 0
  wordCount.value = 0
  pasteWarning.value = false
  isViewingHistory.value = false
  aiScore.value = null

  const backendLevel = level === 1 ? 1 : 3
  try {
    const json = await fetchJson(`${BASE}/writing/prompt?level=${backendLevel}&langCode=${currentLang.value}`)
    if (json.code === 200 && json.data) { prompt.value = json.data }
    else { error.value = '加载题目失败'; prompt.value = null }
  } catch (e) { error.value = '网络错误，请稍后重试'; prompt.value = null }
  finally { loading.value = false }
}

loadPrompt(mapTargetLevelToWriting(authStore.targetLevel))

function onInput() {
  revisionCount.value++
  const text = submittedText.value.trim()
  wordCount.value = (currentLang.value === 'ja' || currentLang.value === 'ko') ? text.length : text.split(/\s+/).filter(Boolean).length
}

function onPaste(e) {
  const text = (e.clipboardData || window.clipboardData).getData('text')
  if (text.length > 100) { e.preventDefault(); pasteWarning.value = true; setTimeout(() => (pasteWarning.value = false), 3000) }
}

function onFocus() { focusTimerId = setInterval(() => { focusTime.value++ }, 1000) }
function onBlur() { if (focusTimerId) clearInterval(focusTimerId) }

async function submit() {
  const userId = authStore.user?.id
  if (!userId) { error.value = '请先登录'; return }
  if (!submittedText.value.trim()) { error.value = '内容不能为空'; return }

  submitLoading.value = true
  try {
    const json = await fetchJson(`${BASE}/writing/submit`, {
      method: 'POST',
      body: {
        userId: Number(userId), langCode: currentLang.value, level: currentLevel.value,
        text: submittedText.value, revisionCount: revisionCount.value,
        topic: prompt.value?.topic || '', type: prompt.value?.type || '',
        promptJson: JSON.stringify(prompt.value || {}),
      },
    })
    if (json.code === 200) {
      submitted.value = true
      aiScore.value = null
      currentHistoryId.value = json.data?.historyId || null
    }
    else { error.value = json.msg || '提交失败' }
  } catch (e) { error.value = '网络错误，请稍后重试' }
  finally { submitLoading.value = false }
}

async function saveScoreToHistory() {
  const userId = authStore.user?.id
  if (!userId || !aiScore.value) return
  try {
    await fetchJson(`${BASE}/writing/save-score`, {
      method: 'POST',
      body: {
        userId: Number(userId),
        historyId: currentHistoryId.value,
        score: aiScore.value.overall || 0,
        scoreDetail: JSON.stringify(aiScore.value),
      },
    })
  } catch (e) { /* 静默 */ }
}
</script>

<template>
  <div class="wp-root">
    <!-- 顶部标题 -->
    <header class="wp-header">
      <LetterSwapTitle :text="(LANG_NAMES[currentLang] || '') + ' 写作训练'" tag="h1" />
      <p class="wp-header-desc">提升写作能力，从模仿到原创</p>
    </header>

    <!-- 模式切换 · 胶囊按钮 -->
    <div class="wp-mode-switch">
      <button
        v-for="lv in writingLevels" :key="lv.level"
        class="btn"
        :class="currentLevel === lv.level ? 'btn-primary' : 'btn-ghost'"
        @click="loadPrompt(lv.level)"
      >
        <span class="wp-mode-icon icon-svg" :class="lv.icon" />
        <div class="wp-mode-text">
          <span class="wp-mode-label">{{ lv.label }}</span>
          <span class="wp-mode-desc">{{ lv.desc }}</span>
        </div>
      </button>
    </div>

    <!-- AI 出题栏 -->
    <div class="wp-ai-bar">
      <div class="wp-ai-input-wrapper">
        <span class="wp-ai-sparkle icon-svg sparkles" />
        <input v-model="aiTopicInput" class="wp-ai-input"
          :placeholder="currentLevel === 1 ? '输入替换主题，留空随机...' : '输入写作主题，留空由 AI 生成...'"
          @keyup.enter="aiGeneratePrompt(currentLevel)" />
      </div>
      <button class="btn btn-primary" :disabled="aiGenerating" @click="aiGeneratePrompt(currentLevel)">
        <span v-if="!aiGenerating">生成题目</span>
        <span v-else class="wp-ai-loading">生成中</span>
      </button>
    </div>

    <!-- 状态区 -->
    <div v-if="error" class="wp-error">
      <span class="wp-error-icon">!</span>{{ error }}
    </div>
    <LoadingSpinner v-if="loading" class="wp-spinner" />

    <!-- ============ 写作卡片 ============ -->
    <div v-if="!loading && prompt" class="wp-card">
      <!-- 题目区 -->
      <div class="wp-prompt-area">
        <div class="wp-prompt-top">
          <span class="wp-prompt-tag" :class="{ ai: prompt.aiGenerated }">{{ prompt.type }}</span>
          <span v-if="prompt.aiGenerated" class="wp-ai-badge">AI</span>
        </div>
        <blockquote v-if="prompt.template" class="wp-template">{{ prompt.template }}</blockquote>
        <blockquote v-if="prompt.topic" class="wp-template">{{ prompt.topic }}</blockquote>
        <p class="wp-instruction">{{ prompt.instruction }}</p>
        <div v-if="prompt.requiredWords?.length" class="wp-req-words">
          <span class="wp-req-label">必用词汇</span>
          <span v-for="w in prompt.requiredWords" :key="w" class="wp-req-chip">{{ w }}</span>
        </div>
        <div v-if="prompt.wordLimit" class="wp-word-limit">
          <span class="wp-limit-icon icon-svg clock" /> 字数 {{ prompt.wordLimit }}
        </div>
      </div>

      <!-- 写作区 -->
      <div v-if="!submitted" class="wp-write-area">
        <div class="wp-textarea-wrapper">
          <textarea
            v-model="submittedText"
            class="wp-textarea"
            :placeholder="currentLevel === 1 ? '仿写你的句子...' : '开始写作...'"
            @input="onInput" @paste="onPaste" @focus="onFocus" @blur="onBlur"
            rows="8"
          />
          <div class="wp-textarea-stats">
            <span class="wp-stat"><b>{{ wordCount }}</b> {{ (currentLang === 'ja' || currentLang === 'ko') ? '字' : '词' }}</span>
            <span class="wp-stat"><b>{{ revisionCount }}</b> 次修改</span>
            <span v-if="focusTime" class="wp-stat"><b>{{ Math.floor(focusTime / 60) }}:{{ String(focusTime % 60).padStart(2, '0') }}</b> 专注</span>
          </div>
        </div>
        <p v-if="pasteWarning" class="wp-paste-warn">请原创写作，勿粘贴外部文本</p>
        <button class="btn btn-primary btn-lg btn-block" :disabled="!submittedText.trim() || submitLoading" @click="submit">
          {{ submitLoading ? '提交中...' : '提交写作' }}
        </button>
      </div>

      <!-- 已提交 -->
      <div v-else class="wp-done-area">
        <div class="wp-done-check" :class="{ history: isViewingHistory }"><span v-if="isViewingHistory" class="icon-svg book" /><span v-else class="icon-svg check" /></div>
        <h3>{{ isViewingHistory ? '历史写作' : '写作已提交' }}</h3>

        <!-- 原文展示 -->
        <div class="wp-submitted-view">{{ submittedText }}</div>

        <div v-if="!aiScore && !isViewingHistory" class="wp-score-ask">
          <p>让 AI 为你的作文打分</p>
          <button class="btn btn-secondary" :disabled="scoreLoading" @click="requestAIScore">
            {{ scoreLoading ? '评分中...' : 'AI 智能评分' }}
          </button>
        </div>

        <!-- 评分报告 -->
        <div v-if="aiScore" class="wp-score-card">
          <div class="wp-score-header">评分报告</div>
          <div class="wp-score-grid">
            <div class="wp-score-item">
              <div class="wp-score-ring" :style="{ '--p': (aiScore.grammar || 0) * 3.6 + 'deg' }">
                <span class="wp-score-num">{{ aiScore.grammar || 0 }}</span>
              </div>
              <span class="wp-score-name">语法</span>
            </div>
            <div class="wp-score-item">
              <div class="wp-score-ring" :style="{ '--p': (aiScore.vocabulary || 0) * 3.6 + 'deg' }">
                <span class="wp-score-num">{{ aiScore.vocabulary || 0 }}</span>
              </div>
              <span class="wp-score-name">词汇</span>
            </div>
            <div class="wp-score-item">
              <div class="wp-score-ring" :style="{ '--p': (aiScore.coherence || 0) * 3.6 + 'deg' }">
                <span class="wp-score-num">{{ aiScore.coherence || 0 }}</span>
              </div>
              <span class="wp-score-name">连贯</span>
            </div>
            <div class="wp-score-item highlight">
              <div class="wp-score-ring big" :style="{ '--p': (aiScore.overall || 0) * 3.6 + 'deg' }">
                <span class="wp-score-num">{{ aiScore.overall || 0 }}</span>
              </div>
              <span class="wp-score-name">总分</span>
            </div>
          </div>
          <div v-if="aiScore.suggestions?.length" class="wp-suggestions">
            <h4>修改建议</h4>
            <div v-for="(s, i) in aiScore.suggestions" :key="i" class="wp-suggestion">{{ i + 1 }}. {{ s }}</div>
          </div>
          <button class="btn btn-secondary" @click="saveScoreToHistory">保存评分记录</button>
        </div>

        <button class="btn btn-secondary" @click="loadPrompt(currentLevel)">再写一次</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="!loading && !error" class="wp-empty">
      <div class="wp-empty-icon"><span class="icon-svg pen" /></div>
      <p>选择一个模式，或使用 AI 生成专属题目</p>
    </div>

    <!-- 写作历史 -->
    <section class="wp-history">
      <button class="btn btn-ghost btn-block" @click="toggleHistory">
        <span>学习记录</span>
        <span class="wp-toggle-arrow" :class="{ open: showWritingHistory }">▾</span>
      </button>
      <div v-if="showWritingHistory" class="wp-history-body">
        <div v-if="whLoading" class="wp-history-empty">加载中...</div>
        <div v-else-if="writingHistory.length === 0" class="wp-history-empty">暂无记录</div>
        <div v-for="w in writingHistory" :key="w.id" class="wp-history-item" @click="viewWritingDetail(w)">
          <span class="wh-title">{{ w.topic || '无主题' }}</span>
          <span class="wh-badges">
            <span class="wh-type">{{ w.type }}</span>
            <span class="wh-score" v-if="w.score">{{ w.score }}分</span>
          </span>
          <span class="wh-date">{{ (w.submitted_at || '').substring(0, 10) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ======================================== */
/*  WRITING PRACTICE · 全新视觉             */
/* ======================================== */
.wp-root {
  max-width: 720px; margin: 0 auto; padding: 24px 20px 60px; min-height: 100vh;
}

/* ---- 顶部 ---- */
.wp-header { text-align: center; padding: 12px 0 4px; }
.wp-header :deep(.letter-swap-title) { font-size: 26px; font-weight: 800; color: #2c3e50; letter-spacing: -0.5px; }
.wp-header-desc { font-size: 13px; color: #94a3b8; margin-top: 4px; }

/* ---- 模式切换 ---- */
.wp-mode-switch { display: flex; gap: 14px; justify-content: center; margin: 24px 0; }
.wp-mode-icon { font-size: 22px; }
.wp-mode-text { display: flex; flex-direction: column; align-items: flex-start; }
.wp-mode-label { font-size: 15px; font-weight: 700; color: #334155; }
.wp-mode-desc { font-size: 11px; color: #94a3b8; }

/* ---- AI 出题栏 ---- */
.wp-ai-bar { display: flex; gap: 10px; justify-content: center; margin-bottom: 20px; }
.wp-ai-input-wrapper {
  flex: 1; max-width: 420px; display: flex; align-items: center; gap: 10px;
  padding: 0 16px; border-radius: 14px; border: 1.5px solid #e8ecf1;
  background: #fff; transition: border-color 0.25s, box-shadow 0.25s;
}
.wp-ai-input-wrapper:focus-within { border-color: #b07c4f; box-shadow: 0 0 0 4px rgba(176,124,79,0.06); }
.wp-ai-sparkle { font-size: 16px; opacity: 0.6; }
.wp-ai-input {
  flex: 1; padding: 12px 0; border: none; background: transparent;
  font-size: 14px; color: #334155; outline: none;
}
.wp-ai-input::placeholder { color: #c0c8d4; }
.wp-ai-loading { animation: pulse 1.2s infinite; }

/* ---- 提示区 ---- */
.wp-error {
  display: flex; align-items: center; gap: 8px; justify-content: center;
  padding: 12px 20px; margin: 0 auto 16px; max-width: 480px;
  background: #fef2f2; color: #dc2626; border-radius: 12px; font-size: 13px;
}
.wp-error-icon {
  width: 20px; height: 20px; border-radius: 50%; background: #dc2626;
  color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 800;
}
.wp-spinner { margin: 40px auto; }

/* ---- 写作卡片 ---- */
.wp-card {
  background: #fff; border-radius: 20px; border: 1.5px solid #f0f2f5;
  box-shadow: 0 4px 24px rgba(0,0,0,0.04); overflow: hidden;
}

/* ---- 题目区 ---- */
.wp-prompt-area { padding: 24px 28px; border-bottom: 1px solid #f5f6f8; }
.wp-prompt-top { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.wp-prompt-tag {
  padding: 4px 14px; border-radius: 20px; background: #eff6ff; color: #6e7a6b;
  font-size: 12px; font-weight: 600;
}
.wp-prompt-tag.ai { background: #f5f3ff; color: #b07c4f; }
.wp-ai-badge {
  padding: 2px 10px; border-radius: 20px; background: linear-gradient(135deg, #b07c4f15, #b07c4f15);
  color: #b07c4f; font-size: 10px; font-weight: 700; letter-spacing: 0.5px;
}
.wp-template {
  margin: 0 0 12px; padding: 16px 20px; border-radius: 12px;
  background: #f8fafc; border-left: 3px solid #5a7d96;
  font-size: 15px; color: #475569; line-height: 1.7; font-family: Georgia, serif;
}
.wp-instruction { font-size: 13px; color: #94a3b8; margin: 0 0 10px; line-height: 1.6; }
.wp-req-words { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.wp-req-label { font-size: 12px; color: #94a3b8; }
.wp-req-chip {
  padding: 3px 12px; border-radius: 20px; background: #eff6ff; color: #6e7a6b;
  font-size: 12px; font-weight: 600;
}
.wp-word-limit { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #94a3b8; }
.wp-limit-icon { font-size: 13px; }

/* ---- 写作区 ---- */
.wp-write-area { padding: 20px 28px 28px; }
.wp-textarea-wrapper {
  border: 1.5px solid #e8ecf1; border-radius: 14px; overflow: hidden;
  transition: border-color 0.25s, box-shadow 0.25s;
  background: #fafbfc;
}
.wp-textarea-wrapper:focus-within { border-color: #7c9db5; box-shadow: 0 0 0 4px rgba(124,157,181,0.08); }
.wp-textarea {
  width: 100%; padding: 18px 20px; border: none; background: transparent;
  font-size: 15px; color: #334155; line-height: 1.8; resize: vertical; outline: none;
  font-family: 'Segoe UI', system-ui, sans-serif;
}
.wp-textarea::placeholder { color: #cbd5e1; }
.wp-textarea-stats {
  display: flex; gap: 20px; padding: 10px 20px; border-top: 1px solid #f0f2f5;
  background: #fff;
}
.wp-stat { font-size: 12px; color: #94a3b8; }
.wp-stat b { color: #64748b; font-weight: 600; }
.wp-paste-warn { padding: 10px 16px; background: #fef2f2; color: #dc2626; border-radius: 10px; font-size: 13px; margin-top: 14px; }
/* ---- 已提交 ---- */
.wp-done-area { padding: 32px 28px; text-align: center; }
.wp-done-check {
  width: 56px; height: 56px; margin: 0 auto 12px; border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 28px; font-weight: 700;
  box-shadow: 0 4px 16px rgba(34,197,94,0.25);
}
.wp-done-check .icon-svg::after { background: #fff; }
.wp-done-area h3 { margin: 0 0 16px; font-size: 20px; font-weight: 700; color: #334155; }

.wp-submitted-view {
  text-align: left; padding: 16px 20px; margin: 0 0 20px; border-radius: 14px;
  background: #fafbfc; border: 1px solid #f0f2f5; font-size: 14px;
  color: #475569; line-height: 1.8; white-space: pre-wrap;
}

/* AI 评分入口 */
.wp-score-ask p { font-size: 14px; color: #94a3b8; margin: 0 0 12px; }
/* 评分报告 */
.wp-score-card { margin-top: 24px; padding: 24px; background: #f8fafc; border-radius: 18px; text-align: left; }
.wp-score-header { font-size: 15px; font-weight: 700; color: #334155; text-align: center; margin-bottom: 20px; }
.wp-score-grid { display: flex; gap: 12px; justify-content: center; margin-bottom: 18px; }
.wp-score-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.wp-score-ring {
  --p: 0deg;
  width: 60px; height: 60px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: conic-gradient(#5a7d96 var(--p), #e8ecf1 0deg);
  position: relative;
}
.wp-score-ring::before {
  content: ''; position: absolute; width: 44px; height: 44px; border-radius: 50%; background: #fff;
}
.wp-score-ring.big { width: 72px; height: 72px; }
.wp-score-ring.big::before { width: 54px; height: 54px; }
.wp-score-num { position: relative; font-size: 16px; font-weight: 800; color: #334155; z-index: 1; }
.wp-score-ring.big .wp-score-num { font-size: 20px; }
.wp-score-item.highlight .wp-score-ring { background: conic-gradient(#b07c4f var(--p), #e8ecf1 0deg); }
.wp-score-name { font-size: 12px; color: #94a3b8; font-weight: 500; }

.wp-suggestions { margin-top: 4px; }
.wp-suggestions h4 { font-size: 13px; font-weight: 700; color: #334155; margin: 0 0 10px; }
.wp-suggestion { padding: 8px 12px; font-size: 13px; color: #64748b; border-left: 2px solid #e8ecf1; margin-bottom: 6px; line-height: 1.6; }

/* ---- 空态 ---- */
.wp-empty { text-align: center; padding: 60px 20px; }
.wp-empty-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.3; }
.wp-empty p { font-size: 14px; color: #94a3b8; }

/* ---- 历史 ---- */
.wp-history { margin-top: 32px; }
.wp-toggle-arrow { transition: transform 0.25s; font-size: 12px; }
.wp-toggle-arrow.open { transform: rotate(180deg); }

.wp-history-body { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.wp-history-item {
  display: flex; align-items: center; gap: 10px; padding: 12px 16px;
  border-radius: 12px; background: #fff; border: 1px solid #f0f2f5; cursor: pointer;
  transition: all 0.2s;
}
.wp-history-item:hover { background: #f8fafc; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
.wh-title { flex: 1; font-size: 13px; font-weight: 600; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wh-badges { display: flex; gap: 6px; }
.wh-type { padding: 2px 10px; border-radius: 20px; background: #eff6ff; color: #6e7a6b; font-size: 11px; font-weight: 600; }
.wh-score { padding: 2px 10px; border-radius: 20px; background: #f0fdf4; color: #16a34a; font-size: 11px; font-weight: 700; }
.wh-date { font-size: 11px; color: #cbd5e1; white-space: nowrap; }
.wp-history-empty { text-align: center; padding: 20px; color: #94a3b8; font-size: 13px; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>

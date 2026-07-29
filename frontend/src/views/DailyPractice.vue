<script setup>
/**
 * DailyPractice.vue — 每日巩固练习 (优化版)
 *
 * 题型: 选择题(释义匹配) + 拼写题(看释义写单词)
 * 每轮 10 题, 优先从收藏词汇抽取, 不足则从全词汇池补齐
 * 支持键盘快捷键、自动推进、今日统计
 */
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { useFavoriteStore } from '@/stores/favorite'
import { useHesitationTracker } from '@/composables/useHesitationTracker'
import { API_BASE_URL } from '@/config'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'

const router = useRouter()
const vocabularyStore = useVocabularyStore()
const languageStore = useLanguageStore()
const authStore = useAuthStore()
const favoriteStore = useFavoriteStore()
const { trackKey, getStats, reset } = useHesitationTracker()

const TOTAL_QUESTIONS = 10
const state = ref('setup')
const questions = ref([])
const currentIndex = ref(0)
const userAnswer = ref('')
const score = ref(0)
const answers = ref([])

const selectedLang = ref('')
const setupError = ref('')

const isCorrect = ref(null)
const showFeedback = ref(false)

const progress = computed(() => Math.round((currentIndex.value / TOTAL_QUESTIONS) * 100))
const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const isLastQuestion = computed(() => currentIndex.value >= TOTAL_QUESTIONS - 1)

// 今日统计
const todayStats = ref({ streak: 0, studiedToday: 0 })

let advanceTimer = null
function clearAdvanceTimer() {
  if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null }
}

onMounted(async () => {
  await languageStore.fetchLanguages()
  await vocabularyStore.fetchVocabularies({ langCode: authStore.targetLanguage, pageSize: 500 })
  if (authStore.isLoggedIn && authStore.user) {
    await favoriteStore.fetchFavorites({ userId: authStore.user.id, pageSize: 500 })
    if (authStore.targetLanguage) selectedLang.value = authStore.targetLanguage
  }
  await loadTodayStats()
})

watch(() => authStore.targetLanguage, async (newLang) => {
  selectedLang.value = newLang
  await vocabularyStore.fetchVocabularies({ langCode: newLang, pageSize: 500 })
})

async function loadTodayStats() {
  const userId = localStorage.getItem('userId')
  if (!userId) return
  try {
    const r = await fetch(`${API_BASE_URL}/practice/today-stats?userId=${userId}`)
    const j = await r.json()
    if (j.code === 200 && j.data) todayStats.value = j.data
  } catch (e) { /* 非关键 */ }
}

/** 构建词汇池：收藏优先，不足则从全词库补齐 */
function buildVocabularyPool() {
  const favIds = new Set(favoriteStore.favorites.map(f => f.vocabId))
  const favWords = vocabularyStore.vocabularyList.filter(v => {
    if (!favIds.has(v.id)) return false
    if (selectedLang.value && v.langCode !== selectedLang.value) return false
    return true
  })
  if (favWords.length >= 4) return favWords

  // 收藏不足，从全词库补齐
  const others = vocabularyStore.vocabularyList.filter(v => {
    if (favIds.has(v.id)) return false
    if (selectedLang.value && v.langCode !== selectedLang.value) return false
    return true
  })
  const pool = [...favWords, ...others]
  if (pool.length < 4) {
    setupError.value = `词汇池不足 4 个（当前 ${pool.length} 个），请确认已选择语言并加载词汇数据。`
    return null
  }
  setupError.value = ''
  return pool
}

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]] }
  return a
}

function startPractice() {
  setupError.value = ''
  const pool = buildVocabularyPool()
  if (!pool) return

  const selected = shuffle(pool).slice(0, TOTAL_QUESTIONS)
  questions.value = selected.map(word => {
    const others = shuffle(pool.filter(w => w.id !== word.id)).slice(0, 3)
    if (Math.random() > 0.3) {
      // 选择：看单词选释义 或 看释义选单词
      if (Math.random() > 0.5) {
        return { type:'choice', id:word.id, prompt:`单词 "${word.word}" 的意思是？`, correct:word.definition, options:shuffle([word.definition, ...others.map(o=>o.definition)]) }
      }
      return { type:'choice', id:word.id, prompt:`"${word.definition}" 对应的单词是？`, correct:word.word, options:shuffle([word.word, ...others.map(o=>o.word)]) }
    }
    return { type:'spell', id:word.id, prompt:`请输入 "${word.definition}" 对应的单词`, hint:word.phonetic||'', correct:word.word }
  })

  state.value = 'practicing'
  currentIndex.value = 0; userAnswer.value = ''; score.value = 0; answers.value = []
  isCorrect.value = null; showFeedback.value = false
}

async function submitAnswer() {
  if (!currentQuestion.value || showFeedback.value) return
  const q = currentQuestion.value
  let correct = q.type === 'choice'
    ? String(userAnswer.value).trim() === String(q.correct).trim()
    : String(userAnswer.value).trim().toLowerCase() === String(q.correct).trim().toLowerCase()

  if (correct) score.value++
  const stats = getStats()
  const hesitationMs = stats.avgMs || 0

  const userId = localStorage.getItem('userId')
  if (userId) {
    fetch(`${API_BASE_URL}/practice/record`, {
      method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({ userId:Number(userId), vocabId:q.id, langCode:authStore.user?.currentLangCode||'en', correct, hesitationMs, errorType:correct?null:(q.type==='spell'?'spelling':'vocabulary') }),
    }).catch(()=>{})
  }

  answers.value.push({ ...q, userAnswer:userAnswer.value, correct })
  isCorrect.value = correct; showFeedback.value = true; reset()

  // 自动推进（保存定时器 ID 防止竞态）
  clearAdvanceTimer()
  advanceTimer = setTimeout(() => nextQuestion(), correct ? 800 : 2000)
}

function nextQuestion() {
  clearAdvanceTimer()
  if (currentIndex.value >= questions.value.length - 1) {
    state.value = 'result'
    loadTodayStats() // 刷新今日统计
    return
  }
  currentIndex.value++
  userAnswer.value = ''; isCorrect.value = null; showFeedback.value = false
}

function restart() { state.value = 'setup' }

// ===== 键盘快捷键 =====
function onKeydown(e) {
  if (state.value !== 'practicing' || !currentQuestion.value) return
  if (showFeedback.value) {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); nextQuestion(); }
    return
  }
  if (currentQuestion.value.type === 'choice') {
    const keys = ['1','2','3','4']
    const idx = keys.indexOf(e.key)
    if (idx >= 0 && idx < currentQuestion.value.options.length) {
      e.preventDefault(); userAnswer.value = currentQuestion.value.options[idx]
    }
  }
  if (e.key === 'Enter' && userAnswer.value) { e.preventDefault(); submitAnswer() }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearAdvanceTimer()
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="每日巩固练习" tag="h1" />
      <p class="page-sub">每天10题，温故知新</p>
    </div>

    <!-- ===== 设置阶段 ===== -->
    <div v-if="state === 'setup'" class="setup-card">
      <div v-if="todayStats.streak > 0 || todayStats.studiedToday > 0" class="today-strip">
        <span class="today-badge"> 连续 {{ todayStats.streak }} 天</span>
        <span class="today-badge"> 今日已学 {{ todayStats.studiedToday }} 词</span>
      </div>
      <div class="setup-row" v-if="!selectedLang">
        <label>选择语言</label>
        <select v-model="selectedLang">
          <option value="">全部语言</option>
          <option v-for="l in languageStore.languages" :key="l.code" :value="l.code">
            {{ l.nameCn }}
          </option>
        </select>
      </div>
      <p v-else class="setup-auto-lang">
        当前语言: <strong>{{ selectedLang.toUpperCase() }}</strong> (自动选择)
      </p>
      <div v-if="setupError" class="setup-error">{{ setupError }}</div>
      <p class="setup-info">
        已收藏 <strong>{{ favoriteStore.favorites.length }}</strong> 个单词（不足将从全词库补齐），
        随机抽取 <strong>{{ TOTAL_QUESTIONS }}</strong> 题练习
      </p>
      <button class="start-btn" @click="startPractice">开始练习</button>
      <p class="key-hint"> 提示：选择题按 1-4 选择，拼写题 Enter 提交</p>
    </div>

    <!-- ===== 练习阶段 ===== -->
    <div v-if="state === 'practicing' && currentQuestion" class="practice-card">
      <!-- 进度条 -->
      <div class="progress-header">
        <span>第 {{ currentIndex + 1 }} / {{ questions.length }} 题</span>
        <span>得分: {{ score }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progress + '%' }" />
      </div>

      <!-- 题目 -->
      <div class="question-area">
        <p class="question-prompt">{{ currentQuestion.prompt }}</p>
        <p v-if="currentQuestion.hint" class="question-hint">
          提示: {{ currentQuestion.hint }}
        </p>

        <!-- 选择题 -->
        <div v-if="currentQuestion.type === 'choice'" class="choice-options">
          <button
            v-for="(opt, i) in currentQuestion.options"
            :key="i"
            class="choice-btn"
            :class="{
              selected: userAnswer === opt,
              'correct-show': showFeedback && opt === currentQuestion.correct,
              'wrong-show': showFeedback && userAnswer === opt && opt !== currentQuestion.correct,
            }"
            :disabled="showFeedback"
            @click="userAnswer = opt"
          >
            <span class="choice-num">{{ i + 1 }}</span>
            <span class="choice-text">{{ opt }}</span>
          </button>
        </div>

        <!-- 拼写题 -->
        <div v-else class="spell-area">
          <input
            v-model="userAnswer"
            type="text"
            class="spell-input"
            @keydown="trackKey"
            placeholder="输入单词..."
            :disabled="showFeedback"
            @keyup.enter="!showFeedback && submitAnswer()"
          />
        </div>
      </div>

      <!-- 反馈 -->
      <div v-if="showFeedback" class="feedback-area">
        <p v-if="isCorrect" class="feedback-correct"> 回答正确！</p>
        <p v-else class="feedback-wrong">
          正确答案是：
          <strong>{{ currentQuestion.correct }}</strong>
        </p>
      </div>

      <!-- 操作按钮 -->
      <div class="action-row">
        <button
          v-if="!showFeedback"
          class="submit-btn"
          :disabled="!userAnswer"
          @click="submitAnswer"
        >提交</button>
        <button v-else class="next-btn" @click="nextQuestion">
          {{ isLastQuestion ? '查看结果' : '下一题' }}
        </button>
      </div>
    </div>

    <!-- ===== 结果阶段 ===== -->
    <div v-if="state === 'result'" class="result-card">
      <div class="result-header">
        <template v-if="score === TOTAL_QUESTIONS"> 全部正确！太棒了！</template>
        <template v-else-if="score >= 7"> 做得不错！</template>
        <template v-else-if="score >= 5"> 继续加油！</template>
        <template v-else> 多多练习！</template>
      </div>
      <div class="result-score">
        {{ score }} / {{ TOTAL_QUESTIONS }}
      </div>
      <div class="result-meter">
        <div class="result-fill" :style="{ width: (score / TOTAL_QUESTIONS * 100) + '%' }" />
      </div>

      <!-- 错题回顾 -->
      <div v-if="answers.some((a) => !a.correct)" class="wrong-list">
        <h3>错误回顾</h3>
        <div v-for="(a, i) in answers.filter((a) => !a.correct)" :key="i" class="wrong-item">
          <span class="wrong-q">{{ a.prompt }}</span>
          <span class="wrong-a">你的答案: <strong>{{ a.userAnswer }}</strong></span>
          <span class="wrong-c">正确答案: <strong>{{ a.correct }}</strong></span>
        </div>
      </div>

      <button class="restart-btn" @click="restart">再来一轮</button>
    </div>
  </div>
</template>

<style scoped>
/* ===== 页面 ===== */
.page-header {
  text-align: center;
  padding: 24px 0 10px;
}
.page-header :deep(.letter-swap-title) {
  font-size: 30px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 6px;
}
.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
}

/* ===== 设置卡片 ===== */
.setup-card {
  max-width: 440px;
  margin: 28px auto;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 32px 30px;
  text-align: center;
}
.setup-row {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
  margin-bottom: 16px;
}
.setup-row label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.setup-row select {
  padding: 10px 16px;
  border-radius: 10px;
  border: 1.5px solid #e0e0e0;
  background: #fafafa;
  color: var(--color-text);
  font-size: 14px;
}

/* 今日统计条 */
.today-strip { display: flex; gap: 12px; justify-content: center; margin-bottom: 18px; }
.today-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: var(--radius-full);
  font-size: 12px; font-weight: 600;
  background: rgba(39,174,96,0.1); color: #27ae60;
}

.setup-info {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 22px;
}
.start-btn {
  padding: 13px 40px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(90, 125, 150, 0.25);
  transition: all 0.25s;
}
.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(90, 125, 150, 0.35);
}

.key-hint {
  margin-top: 14px; font-size: 12px; color: #aaa; text-align: center;
}

/* ===== 练习卡片 ===== */
.practice-card {
  max-width: 600px;
  margin: 28px auto;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 32px 30px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}
.progress-bar {
  height: 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
  margin-bottom: 28px;
}
.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #7c9db5, #5a7d96);
  transition: width 0.4s ease;
}

.question-prompt {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}
.question-hint {
  font-size: 14px;
  color: #888;
  margin-bottom: 20px;
  font-style: italic;
}

/* 选择项 */
.choice-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.choice-btn {
  padding: 14px 18px;
  border-radius: 10px;
  border: 1.5px solid #e8e8e8;
  background: #fafafa;
  color: var(--color-text);
  font-size: 15px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
  display: flex; align-items: center; gap: 10px;
}
.choice-num {
  width: 24px; height: 24px; border-radius: 6px;
  background: rgba(0,0,0,0.06); color: #999;
  font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}
.choice-btn:hover:not(:disabled) {
  border-color: #7c9db5;
  background: rgba(124, 157, 181, 0.05);
}
.choice-btn.selected {
  border-color: #5a7d96;
  background: rgba(90, 125, 150, 0.1);
  font-weight: 600;
}
.choice-btn.correct-show {
  border-color: #27ae60;
  background: #eefaf3;
}
.choice-btn.wrong-show {
  border-color: #e74c3c;
  background: #fef0ef;
}

/* 拼写 */
.spell-input {
  width: 100%;
  padding: 14px 18px;
  border-radius: 10px;
  border: 1.5px solid #e0e0e0;
  background: #fafafa;
  color: var(--color-text);
  font-size: 18px;
  outline: none;
  transition: border-color 0.2s;
}
.spell-input:focus {
  border-color: #7c9db5;
  box-shadow: 0 0 0 3px rgba(124, 157, 181, 0.1);
}

/* 反馈 */
.feedback-area {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 10px;
}
.feedback-correct {
  color: #27ae60;
  font-weight: 600;
  font-size: 16px;
}
.feedback-wrong {
  color: #e74c3c;
  font-weight: 500;
  font-size: 14px;
}

/* 按钮 */
.action-row {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
}
.submit-btn {
  padding: 11px 28px;
  border-radius: 10px;
  border: none;
  background: #5a7d96;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
}
.submit-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.submit-btn:hover:not(:disabled) { background: #4a6d86; }
.next-btn {
  padding: 11px 28px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 3px 12px rgba(90, 125, 150, 0.25);
  transition: all 0.25s;
}
.next-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(90, 125, 150, 0.35);
}

/* ===== 结果卡片 ===== */
.result-card {
  max-width: 500px;
  margin: 28px auto;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-lg);
  padding: 36px 30px;
  text-align: center;
}
.result-header {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 6px;
}
.result-score {
  font-size: 48px;
  font-weight: 800;
  color: #5a7d96;
  margin-bottom: 10px;
}
.result-meter {
  height: 10px;
  border-radius: 5px;
  background: rgba(0, 0, 0, 0.06);
  margin-bottom: 28px;
  overflow: hidden;
}
.result-fill {
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(90deg, #27ae60, #5a7d96);
  transition: width 0.6s ease;
}

/* 错题 */
.wrong-list {
  text-align: left;
  margin-bottom: 24px;
}
.wrong-list h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 12px;
}
.wrong-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 14px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: #fef5f5;
  border: 1px solid #fde0e0;
  font-size: 13px;
}
.wrong-q { color: var(--color-text); font-weight: 500; }
.wrong-a { color: #e74c3c; }
.wrong-c { color: #27ae60; }

.setup-error { margin-bottom: 14px; padding: 10px 16px; background: #fef5f5; color: #e74c3c; border-radius: 10px; font-size: 13px; }
.restart-btn {
  padding: 13px 36px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(90, 125, 150, 0.25);
  transition: all 0.25s;
}
.restart-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(90, 125, 150, 0.35);
}
</style>

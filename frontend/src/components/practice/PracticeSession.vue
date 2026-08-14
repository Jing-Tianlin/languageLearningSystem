<script setup>
/**
 * PracticeSession.vue — 巩固练习（学习-练习闭环）
 *
 * 接收一轮背单词产生的 words，生成混合题（选择/听音/拼写），
 * 带连击激励，完成后 emit done({ score, total, maxCombo, wrongIds })
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useVocabularyStore } from '@/stores/vocabulary'
import { useAuthStore } from '@/stores/auth'
import { useHesitationTracker } from '@/composables/useHesitationTracker'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'

const props = defineProps({
  words: { type: Array, required: true },
  langCode: { type: String, default: 'en' },
})
const emit = defineEmits(['done', 'skip'])

const vocabularyStore = useVocabularyStore()
const authStore = useAuthStore()
const { trackKey, getStats, reset } = useHesitationTracker()

const TOTAL = computed(() => Math.min(10, props.words.filter(w => w.word && w.definition).length))
const state = ref('practicing')
const questions = ref([])
const currentIndex = ref(0)
const userAnswer = ref('')
const score = ref(0)
const answers = ref([])
const isCorrect = ref(null)
const showFeedback = ref(false)
const combo = ref(0)
const maxCombo = ref(0)
const speaking = ref(false)

const progress = computed(() => questions.value.length > 0 ? Math.round((currentIndex.value / questions.value.length) * 100) : 0)
const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const isLastQuestion = computed(() => currentIndex.value >= questions.value.length - 1)

let advanceTimer = null
function clearAdvanceTimer() {
  if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null }
}

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]] }
  return a
}

/** 干扰项来源：全词库优先，不足从本轮词补齐 */
function distractorSource(word) {
  const all = vocabularyStore.vocabularyList || []
  const others = all.filter(v => v.id !== word.id && v.definition)
  if (others.length >= 3) return others
  return [...others, ...props.words.filter(w => w.id !== word.id && w.definition)]
}

function buildQuestions() {
  const pool = props.words.filter(w => w.word && w.definition)
  if (pool.length === 0) { emit('skip'); return }
  const selected = shuffle(pool).slice(0, Math.min(10, pool.length))
  questions.value = selected.map(word => {
    const others = shuffle(distractorSource(word)).slice(0, 3)
    const roll = Math.random()
    if (roll < 0.3) {
      // 听音选义
      return { type:'listen', id:word.id, word:word.word, correct:word.definition, options:shuffle([word.definition, ...others.map(o=>o.definition)]) }
    }
    if (roll < 0.5) {
      // 拼写
      return { type:'spell', id:word.id, word:word.word, prompt:`请输入 "${word.definition}" 对应的单词`, hint:word.phonetic||'', correct:word.word }
    }
    // 选择
    if (Math.random() > 0.5) {
      return { type:'choice', id:word.id, word:word.word, prompt:`单词 "${word.word}" 的意思是？`, correct:word.definition, options:shuffle([word.definition, ...others.map(o=>o.definition)]) }
    }
    return { type:'choice', id:word.id, word:word.word, prompt:`"${word.definition}" 对应的单词是？`, correct:word.word, options:shuffle([word.word, ...others.map(o=>o.word)]) }
  })
}

function speak(text, lang = props.langCode) {
  if (speaking.value || !text) return
  const langMap = { en:'en-US', ja:'ja-JP', ko:'ko-KR', fr:'fr-FR', de:'de-DE' }
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = langMap[lang] || 'en-US'
  utterance.rate = 0.85
  utterance.onstart = () => { speaking.value = true }
  utterance.onend = () => { speaking.value = false }
  utterance.onerror = () => { speaking.value = false }
  speechSynthesis.speak(utterance)
}
function replayListen() {
  if (currentQuestion.value?.type === 'listen') speak(currentQuestion.value.word)
}

async function submitAnswer() {
  if (!currentQuestion.value || showFeedback.value) return
  const q = currentQuestion.value
  let correct
  if (q.type === 'choice' || q.type === 'listen') {
    correct = String(userAnswer.value).trim() === String(q.correct).trim()
  } else {
    correct = String(userAnswer.value).trim().toLowerCase() === String(q.correct).trim().toLowerCase()
  }

  if (correct) { score.value++; combo.value++; maxCombo.value = Math.max(maxCombo.value, combo.value) }
  else { combo.value = 0 }

  // 提交 SRS 记录：答对 4（认识），答错 1（忘记）
  const stats = getStats()
  const hesitationMs = stats.avgMs || 0
  const userId = authStore.user?.id || localStorage.getItem('userId')
  if (userId) {
    fetchJson(`${API_BASE_URL}/practice/record`, {
      method:'POST',
      body:{
        userId:Number(userId), vocabId:q.id, langCode:props.langCode,
        quality: correct ? 4 : 1, hesitationMs,
        errorType: correct ? null : (q.type==='spell' ? 'spelling' : 'vocabulary'),
      },
    }).catch(()=>{})
  }

  answers.value.push({ ...q, userAnswer:userAnswer.value, correct })
  isCorrect.value = correct; showFeedback.value = true; reset()

  clearAdvanceTimer()
  advanceTimer = setTimeout(() => nextQuestion(), correct ? 700 : 1800)
}

function nextQuestion() {
  clearAdvanceTimer()
  if (currentIndex.value >= questions.value.length - 1) {
    finish()
    return
  }
  currentIndex.value++
  userAnswer.value = ''; isCorrect.value = null; showFeedback.value = false
  if (currentQuestion.value?.type === 'listen') setTimeout(() => speak(currentQuestion.value.word), 250)
}

function finish() {
  state.value = 'done'
  const wrongIds = answers.value.filter(a => !a.correct).map(a => a.id)
  emit('done', { score: score.value, total: questions.value.length, maxCombo: maxCombo.value, wrongIds })
}

function skip() { emit('skip') }

// ===== 键盘 =====
function onKeydown(e) {
  if (state.value !== 'practicing' || !currentQuestion.value) return
  if (showFeedback.value) {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); nextQuestion(); }
    return
  }
  if (currentQuestion.value.type === 'choice' || currentQuestion.value.type === 'listen') {
    const idx = ['1','2','3','4'].indexOf(e.key)
    if (idx >= 0 && idx < currentQuestion.value.options.length) {
      e.preventDefault()
      userAnswer.value = currentQuestion.value.options[idx]
      if (currentQuestion.value.type === 'listen') submitAnswer()
    }
  }
  if (e.key === 'Enter' && userAnswer.value) { e.preventDefault(); submitAnswer() }
}

onMounted(() => {
  buildQuestions()
  window.addEventListener('keydown', onKeydown)
  if (questions.value[0]?.type === 'listen') setTimeout(() => speak(questions.value[0].word), 300)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearAdvanceTimer()
  speechSynthesis.cancel()
})
</script>

<template>
  <div v-if="state === 'practicing' && currentQuestion" class="practice-card">
    <div class="practice-head">
      <span class="ph-title">巩固练习</span>
      <button class="skip-btn" @click="skip">跳过 →</button>
    </div>
    <div class="progress-header">
      <span>第 {{ currentIndex + 1 }} / {{ questions.length }} 题</span>
      <span v-if="combo >= 2" class="combo-badge"><span class="icon-svg fire" /> {{ combo }} 连击</span>
      <span>得分: {{ score }}</span>
    </div>
    <div class="progress-bar"><div class="progress-fill" :style="{ width: progress + '%' }" /></div>

    <div class="question-area">
      <!-- 听音题 -->
      <template v-if="currentQuestion.type === 'listen'">
        <button class="btn btn-icon listen-replay" @click="replayListen" :class="{ speaking }"><span class="icon-svg speaker" /> 再听一遍</button>
        <p class="question-prompt">听发音，选出对应的释义</p>
        <div class="choice-options">
          <button
            v-for="(opt, i) in currentQuestion.options" :key="i"
            class="btn"
            :class="[
              userAnswer === opt ? 'btn-secondary' : 'btn-ghost',
              { 'btn-danger': showFeedback && userAnswer === opt && opt !== currentQuestion.correct }
            ]"
            :disabled="showFeedback"
            @click="userAnswer = opt; submitAnswer()"
          >
            <span class="choice-num">{{ i + 1 }}</span>
            <span class="choice-text">{{ opt }}</span>
          </button>
        </div>
      </template>

      <!-- 选择题 -->
      <template v-else-if="currentQuestion.type === 'choice'">
        <p class="question-prompt">{{ currentQuestion.prompt }}</p>
        <p v-if="currentQuestion.hint" class="question-hint">提示: {{ currentQuestion.hint }}</p>
        <div class="choice-options">
          <button
            v-for="(opt, i) in currentQuestion.options" :key="i"
            class="btn"
            :class="[
              userAnswer === opt ? 'btn-secondary' : 'btn-ghost',
              { 'btn-danger': showFeedback && userAnswer === opt && opt !== currentQuestion.correct }
            ]"
            :disabled="showFeedback"
            @click="userAnswer = opt"
          >
            <span class="choice-num">{{ i + 1 }}</span>
            <span class="choice-text">{{ opt }}</span>
          </button>
        </div>
      </template>

      <!-- 拼写题 -->
      <template v-else>
        <p class="question-prompt">{{ currentQuestion.prompt }}</p>
        <p v-if="currentQuestion.hint" class="question-hint">提示: {{ currentQuestion.hint }}</p>
        <div class="spell-area">
          <input
            v-model="userAnswer" type="text" class="spell-input"
            @keydown="trackKey" placeholder="输入单词..."
            :disabled="showFeedback"
            @keyup.enter="!showFeedback && submitAnswer()"
          />
        </div>
      </template>
    </div>

    <!-- 反馈 -->
    <div v-if="showFeedback" class="feedback-area">
      <p v-if="isCorrect" class="feedback-correct">回答正确！</p>
      <p v-else class="feedback-wrong">正确答案是：<strong>{{ currentQuestion.correct }}</strong></p>
    </div>

    <!-- 操作 -->
    <div class="action-row">
      <button v-if="!showFeedback" class="btn btn-primary" :disabled="!userAnswer" @click="submitAnswer">提交</button>
      <button v-else class="btn btn-primary" @click="nextQuestion">{{ isLastQuestion ? '查看结果' : '下一题' }}</button>
    </div>
    <p class="kb-hint">选择/听音 1-4 · 拼写 Enter 提交 · 反馈后 Enter/空格 下一题</p>
  </div>
</template>

<style scoped>
.practice-card {
  max-width: 560px; margin: 0 auto;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 22px 24px 26px;
  animation: card-in 0.25s ease;
}
@keyframes card-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.practice-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.ph-title { font-size: 15px; font-weight: 700; color: var(--color-primary); }
.skip-btn { font-size: 12px; color: #a49ec0; background: none; border: none; cursor: pointer; }
.skip-btn:hover { color: #5f5a70; }
.progress-header { display: flex; justify-content: space-between; font-size: 13px; color: var(--color-text-muted); margin-bottom: 8px; align-items: center; }
.combo-badge {
  padding: 3px 12px; border-radius: 12px;
  background: linear-gradient(135deg, #ff9f6e, #ff9f6e);
  color: #fff; font-size: 12px; font-weight: 700;
  animation: combo-pop 0.3s ease;
}
@keyframes combo-pop { 0% { transform: scale(0.6); } 60% { transform: scale(1.15); } 100% { transform: scale(1); } }
.listen-replay {
  margin-bottom: 10px; padding: 8px 18px; border-radius: 20px;
  border: 1.5px solid #7bb7ff; background: rgba(123,183,255,0.06);
  color: #4d96ff; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.2s;
}
.listen-replay:hover { background: rgba(123,183,255,0.14); transform: translateY(-1px); }
.listen-replay.speaking { border-color: #3fa65a; color: #3fa65a; }
.progress-bar { height: 6px; border-radius: 3px; background: rgba(0,0,0,0.06); margin-bottom: 22px; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #7bb7ff, #4d96ff); transition: width 0.4s ease; }
.question-prompt { font-size: 18px; font-weight: 600; color: var(--color-text); margin-bottom: 8px; }
.question-hint { font-size: 14px; color: #6b647e; margin-bottom: 20px; font-style: italic; }
.choice-options { display: flex; flex-direction: column; gap: 10px; }
.choice-num {
  width: 24px; height: 24px; border-radius: 6px;
  background: rgba(0,0,0,0.06); color: #8f88a8; font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.spell-input {
  width: 100%; padding: 14px 18px; border-radius: 12px;
  border: 1.5px solid #e6e0f2; background: #fafafa;
  color: var(--color-text); font-size: 18px; outline: none; transition: border-color 0.2s;
}
.spell-input:focus { border-color: #7bb7ff; box-shadow: 0 0 0 3px rgba(123,183,255,0.1); }
.feedback-area { margin-top: 16px; padding: 12px 16px; border-radius: 10px; }
.feedback-correct { color: #3fa65a; font-weight: 600; font-size: 16px; }
.feedback-wrong { color: #ff6b6b; font-weight: 500; font-size: 14px; }
.action-row { margin-top: 22px; display: flex; justify-content: flex-end; }
.kb-hint { text-align: center; font-size: 12px; color: #bdb7ce; margin-top: 12px; }
</style>

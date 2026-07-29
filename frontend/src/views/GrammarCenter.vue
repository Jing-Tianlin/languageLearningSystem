<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed, nextTick } from 'vue'
import { useGrammarStore } from '@/stores/grammar'
import { useAuthStore } from '@/stores/auth'
import { getLevelLabel } from '@/data/examLevels'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { LANG_NAMES } from '@/config/languages'

const store = useGrammarStore()
const authStore = useAuthStore()

// 长难句展开状态
const expandedSid = ref(null)

// 同步语言
watch(() => authStore.targetLanguage, (v) => {
  if (v) store.setLang(v)
}, { immediate: true })

// 当前全局等级标签（注册时选择，全局生效）
const currentLevelLabel = computed(() => {
  if (authStore.targetLevel === -1 || authStore.targetLevel === null) return '全部等级'
  return getLevelLabel(store.lang, authStore.targetLevel)
})

function parseGrammar(raw) {
  if (!raw) return []
  try { return JSON.parse(raw) }
  catch { return raw.replace(/[\[\]"]/g, '').split(',').map(s => s.trim()).filter(Boolean) }
}

// ===== 发音 =====
const speaking = ref(false)
function speak(text) {
  if (speaking.value || !text) return
  const langMap = { en:'en-US', ja:'ja-JP', ko:'ko-KR', fr:'fr-FR', de:'de-DE' }
  const u = new SpeechSynthesisUtterance(text)
  u.lang = langMap[store.lang] || 'en-US'
  u.rate = 0.9
  u.onstart = () => { speaking.value = true }
  u.onend = () => { speaking.value = false }
  u.onerror = () => { speaking.value = false }
  speechSynthesis.speak(u)
}

// ===== 逐题练习 =====
const practiceStep = ref('idle') // idle | answering | result
const practiceIndex = ref(0)
const combo = ref(0)
const maxCombo = ref(0)
const practiceAnswersLog = ref([])

const practiceQuestions = computed(() => store.currentPractices)
const currentQuestion = computed(() => practiceQuestions.value[practiceIndex.value] || null)
const practiceTotal = computed(() => practiceQuestions.value.length)
const practiceProgress = computed(() => practiceTotal.value > 0 ? Math.round(((practiceIndex.value + 1) / practiceTotal.value) * 100) : 0)
const practiceScore = computed(() => practiceAnswersLog.value.filter(a => a.correct).length)
const practiceAccuracy = computed(() => practiceTotal.value > 0 ? Math.round((practiceScore.value / practiceTotal.value) * 100) : 0)

function beginPracticeSession() {
  if (!practiceQuestions.value.length) return
  practiceStep.value = 'answering'
  practiceIndex.value = 0
  combo.value = 0
  maxCombo.value = 0
  practiceAnswersLog.value = []
  nextTick(() => { if (currentQuestion.value) speak(currentQuestion.value.question) })
}

function check(q) {
  const ans = (q.userAnswer || '').trim()
  if (!ans) return
  const isCorrect = store.checkAnswer(q, ans)
  practiceAnswersLog.value.push({ id: q.id, correct: isCorrect })
  if (isCorrect) { combo.value++; maxCombo.value = Math.max(maxCombo.value, combo.value) }
  else combo.value = 0
}

function nextPractice() {
  if (practiceIndex.value >= practiceTotal.value - 1) {
    practiceStep.value = 'result'
    store.fetchStats()
    return
  }
  practiceIndex.value++
  nextTick(() => { if (currentQuestion.value) speak(currentQuestion.value.question) })
}

function restartPractice() {
  store.resetPractices()
  practiceStep.value = 'idle'
  practiceAnswersLog.value = []
}

function onPracticeKeydown(e) {
  if (practiceStep.value !== 'answering' || !currentQuestion.value) return
  const q = currentQuestion.value
  const answered = store.practiceAnswers[q.id] !== undefined
  if (e.key === 'Enter') {
    e.preventDefault()
    if (answered) nextPractice()
    else check(q)
  }
}

onMounted(() => {
  if (!store.lessons.length) store.fetchLessons()
  if (!store.sentenceList.length) store.fetchSentences()
  store.fetchStats()
  window.addEventListener('keydown', onPracticeKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onPracticeKeydown)
  speechSynthesis.cancel()
})

// 切换 tab 时自动加载对应全局等级的内容
watch(() => store.tab, (tab) => {
  if (tab === 'practice') {
    store.fetchPractices(authStore.targetLevel)
    practiceStep.value = 'idle'
    practiceAnswersLog.value = []
  }
})
</script>

<template>
  <div>
    <div class="page-header">
      <LetterSwapTitle :text="(LANG_NAMES[store.lang] || '') + ' 语法中心'" tag="h1" />
      <p class="page-sub">
        {{ { en:'时态·介词·从句·长难句', ja:'助词·活用·敬语·长难句', ko:'助词·语尾·敬语·长难句', fr:'变位·时态·形容词·长难句', de:'格·词序·动词·长难句' }[store.lang] || '' }}
      </p>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button class="btn" :class="store.tab === 'learn' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="store.tab = 'learn'">语法教程</button>
      <button class="btn" :class="store.tab === 'practice' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="store.tab = 'practice'">语法练习</button>
      <button class="btn" :class="store.tab === 'sentences' ? 'btn-secondary btn-sm' : 'btn-ghost btn-sm'" @click="store.tab = 'sentences'">长难句</button>
    </div>

    <!-- 统计概览 -->
    <div v-if="store.stats.totalAttempts > 0" class="stats-bar">
      <span>练习 {{ store.stats.totalAttempts }} 次</span>
      <span class="stats-div">|</span>
      <span>正确率 {{ store.stats.accuracy }}%</span>
    </div>

    <!-- ==================== 语法教程 ==================== -->
    <div v-if="store.tab === 'learn'">
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
      <p v-else class="empty-text">暂无语法教程数据</p>
    </div>

    <!-- ==================== 语法练习 ==================== -->
    <div v-if="store.tab === 'practice'">
      <div v-if="practiceStep === 'idle'" class="practice-intro-card">
        <p class="pi-title">语法练习</p>
        <p class="pi-sub">当前等级：{{ currentLevelLabel }}</p>
        <div class="pi-stats" v-if="store.stats.totalAttempts > 0">
          <div class="pi-stat"><span class="pi-stat-num">{{ store.stats.totalAttempts }}</span><span class="pi-stat-lbl">已练习</span></div>
          <div class="pi-stat"><span class="pi-stat-num">{{ store.stats.correctCount }}</span><span class="pi-stat-lbl">答对</span></div>
          <div class="pi-stat"><span class="pi-stat-num">{{ store.stats.accuracy }}%</span><span class="pi-stat-lbl">正确率</span></div>
        </div>
        <LoadingSpinner v-if="store.loading.practices" />
        <template v-else>
          <p v-if="practiceQuestions.length > 0" class="pi-count">本组共 {{ practiceQuestions.length }} 题 · 逐题作答</p>
          <div class="pi-actions">
            <button v-if="practiceQuestions.length > 0" class="btn btn-primary" @click="beginPracticeSession">开始练习</button>
            <button class="btn btn-secondary" @click="store.fetchPractices(authStore.targetLevel)">重新加载</button>
          </div>
          <div v-if="!practiceQuestions.length" class="empty-practice">
            <p>该等级暂无语法练习题</p>
            <button class="btn btn-primary" @click="store.generateAIQuestions(authStore.targetLevel)" :disabled="store.aiLoading">
              {{ store.aiLoading ? 'AI 出题中...' : 'AI 生成新题目' }}
            </button>
          </div>
        </template>
        <div v-if="store.hasAIPractices" class="ai-badge">
          <span>AI 生成题目 · 练习后不记录成绩</span>
          <button class="btn btn-danger btn-sm" @click="store.clearAIPractices()">清除</button>
        </div>
      </div>

      <!-- 逐题作答 -->
      <div v-else-if="practiceStep === 'answering' && currentQuestion" class="session-card">
        <div class="session-head">
          <span class="sh-count">{{ practiceIndex + 1 }}<i>/{{ practiceTotal }}</i></span>
          <span v-if="combo >= 2" class="combo-badge"><span class="icon-svg fire" /> {{ combo }} 连击</span>
          <span class="sh-score">答对 {{ practiceScore }}</span>
        </div>
        <div class="progress-bar"><div class="progress-fill" :style="{ width: practiceProgress + '%' }" /></div>

        <div class="session-question" :class="{
          done: store.practiceAnswers[currentQuestion.id] !== undefined,
          correct: store.practiceAnswers[currentQuestion.id]?.isCorrect,
          wrong: store.practiceAnswers[currentQuestion.id] !== undefined && !store.practiceAnswers[currentQuestion.id]?.isCorrect
        }">
          <div class="sq-top">
            <span class="sq-type">{{ currentQuestion.type === 'fill' ? '填空题' : '纠错题' }}</span>
            <button class="btn btn-icon btn-sm btn-ghost" @click="speak(currentQuestion.question)" :class="{ speaking }"><span class="icon-svg speaker" /></button>
          </div>
          <p class="sq-text">{{ currentQuestion.question }}</p>
          <p v-if="currentQuestion.hint" class="sq-hint">{{ currentQuestion.hint }}</p>
          <div class="sq-row">
            <input
              v-model="currentQuestion.userAnswer"
              class="sq-input"
              :placeholder="currentQuestion.type === 'fill' ? '输入答案...' : '输入正确形式...'"
              :disabled="store.practiceAnswers[currentQuestion.id] !== undefined"
              @keyup.enter="check(currentQuestion)"
            />
            <button
              v-if="store.practiceAnswers[currentQuestion.id] === undefined"
              class="btn btn-primary"
              @click="check(currentQuestion)"
            >提交</button>
          </div>
          <div v-if="store.practiceAnswers[currentQuestion.id]?.isCorrect" class="fb-ok">回答正确！</div>
          <div v-if="store.practiceAnswers[currentQuestion.id] !== undefined && !store.practiceAnswers[currentQuestion.id]?.isCorrect" class="fb-bad">
            <span class="fb-wrong">错误！正确答案：<strong>{{ currentQuestion.answer }}</strong></span>
            <span v-if="currentQuestion.explanation" class="fb-explain">{{ currentQuestion.explanation }}</span>
          </div>
        </div>

        <div class="session-actions">
          <button
            v-if="store.practiceAnswers[currentQuestion.id] !== undefined"
            class="btn btn-primary"
            @click="nextPractice"
          >{{ practiceIndex >= practiceTotal - 1 ? '查看结果' : '下一题 →' }}</button>
        </div>
        <p class="kb-hint">Enter 提交 · 提交后再按 Enter 进入下一题</p>
      </div>

      <!-- 结果页 -->
      <div v-else-if="practiceStep === 'result'" class="result-card">
        <div class="res-ring" :style="{ '--p': practiceAccuracy + '%' }">
          <div class="res-ring-inner">
            <span class="res-num">{{ practiceAccuracy }}%</span>
            <span class="res-lbl">正确率</span>
          </div>
        </div>
        <div class="res-title">
          {{ practiceAccuracy >= 90 ? '语法掌握优秀！' : practiceAccuracy >= 70 ? '语法掌握良好' : '继续加油' }}
        </div>
        <div class="res-stats">
          <div class="res-stat"><span class="res-n">{{ practiceScore }}</span>答对</div>
          <div class="res-stat"><span class="res-n">{{ practiceTotal - practiceScore }}</span>答错</div>
          <div class="res-stat"><span class="res-n">{{ maxCombo }}</span>最高连击</div>
        </div>

        <div v-if="practiceAnswersLog.some(a => !a.correct)" class="res-wrong">
          <h4>错题回顾</h4>
          <div v-for="q in practiceQuestions.filter(q => !practiceAnswersLog.find(a => a.id === q.id)?.correct)" :key="q.id" class="res-wrong-item">
            <p class="rw-q">{{ q.question }}</p>
            <p class="rw-a">正确答案：<strong>{{ q.answer }}</strong><span v-if="q.explanation"> — {{ q.explanation }}</span></p>
          </div>
        </div>

        <div class="res-actions">
          <button class="btn btn-primary btn-lg" @click="restartPractice">再做一组</button>
          <button class="btn btn-secondary" @click="store.generateAIQuestions(authStore.targetLevel)" :disabled="store.aiLoading">
            {{ store.aiLoading ? 'AI 出题中...' : 'AI 换组题' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 长难句精析 ==================== -->
    <div v-if="store.tab === 'sentences'">
      <div v-if="store.dailySentence" class="daily-card">
        <div class="daily-badge-row">
          <span class="daily-badge">
            每日一句 · {{ store.dailySentence.level === 'Advanced' ? '高级' : store.dailySentence.level === 'Intermediate' ? '中级' : '' }}
          </span>
          <div class="daily-actions">
            <button class="btn btn-icon btn-sm btn-ghost speak-btn" @click="store.prevDailySentence()" title="上一句">‹</button>
            <span class="daily-counter">{{ store.sentenceList.length ? store.dailyIndex + 1 + ' / ' + store.sentenceList.length : '' }}</span>
            <button class="btn btn-icon btn-sm btn-ghost speak-btn" @click="store.nextDailySentence()" title="下一句">›</button>
            <button class="btn btn-icon btn-sm btn-ghost speak-btn" @click="speak(store.dailySentence.sentence)" :class="{ speaking }" title="朗读"><span class="icon-svg speaker" /></button>
          </div>
        </div>
        <p class="daily-sentence">{{ store.dailySentence.sentence }}</p>
        <p class="daily-translation">{{ store.dailySentence.translation }}</p>
        <div class="daily-tags">
          <span v-for="(g, i) in parseGrammar(store.dailySentence.grammar_points || store.dailySentence.grammarPoints)" :key="i" class="grammar-tag">{{ g }}</span>
        </div>
        <details class="daily-analysis">
          <summary>查看结构分析</summary>
          <pre class="analysis-text">{{ store.dailySentence.analysis }}</pre>
        </details>
      </div>

      <div v-if="store.sentenceList.length > 0" class="sentence-list">
        <div v-for="s in store.sentenceList" :key="s.id" class="sentence-card"
          :class="{ expanded: expandedSid === s.id }">
          <div class="sentence-header" @click="expandedSid = expandedSid === s.id ? null : s.id">
            <div class="sentence-top-row">
              <span class="sentence-level-tag">{{ s.level === 'Advanced' ? '高级' : '中级' }}</span>
              <span v-if="s.source" class="sentence-source">{{ s.source }}</span>
              <button class="btn btn-icon btn-sm btn-ghost speak-btn" @click.stop="speak(s.sentence)" :class="{ speaking }" title="朗读"><span class="icon-svg speaker" /></button>
              <span class="expand-arrow">{{ expandedSid === s.id ? '▾' : '▸' }}</span>
            </div>
            <p class="sentence-text">{{ s.sentence }}</p>
          </div>
          <div v-if="expandedSid === s.id" class="sentence-body">
            <div class="sentence-section">
              <h4>中文翻译</h4>
              <p>{{ s.translation || '暂无翻译' }}</p>
            </div>
            <div class="sentence-section">
              <h4>语法点</h4>
              <div class="grammar-tags">
                <span v-for="(g, i) in parseGrammar(s.grammar_points || s.grammarPoints)" :key="i" class="grammar-tag">{{ g }}</span>
              </div>
            </div>
            <div class="sentence-section">
              <h4>句子结构分析</h4>
              <pre class="analysis-text">{{ s.analysis || '暂无分析' }}</pre>
            </div>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        icon="book"
        title="暂无长难句数据"
        description="该语言还没有添加长难句，敬请期待"
      />
    </div>
  </div>
</template>

<style scoped>
.page-header { text-align: center; padding: 20px 0 10px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--color-text-muted); }
.empty-text { text-align: center; color: var(--color-text-muted); padding: 60px 0; font-size: 14px; }

/* ===== 通用容器（与全局卡片体系一致） ===== */
.page-wrap { max-width: 760px; margin: 0 auto; padding: 0 16px 40px; }
.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 20px 0; }
.tab-bar button { padding: 10px 24px; border-radius: 20px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s; color: #666; }
.tab-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

/* ===== 教程 ===== */
.learn-area { display: flex; flex-direction: column; gap: 14px; }
.lesson-card {
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06); overflow: hidden;
}
.lesson-header { padding: 18px 22px; font-size: 17px; font-weight: 700; color: var(--color-text); cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.lesson-header:hover { background: rgba(0,0,0,0.02); }
.expand-icon { font-size: 14px; color: #aaa; }
.lesson-body { padding: 0 22px 22px; }
.lesson-section { margin-top: 14px; padding: 14px 18px; background: rgba(90,125,150,0.04); border-radius: 12px; border: 1px solid rgba(90,125,150,0.1); }
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

/* ===== 练习介绍页 ===== */
.practice-intro-card {
  max-width: 520px; margin: 0 auto;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 32px 30px; text-align: center;
}
.pi-title { font-size: 22px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.pi-sub { font-size: 14px; color: #888; margin-top: 6px; }
.pi-stats { display: flex; justify-content: center; gap: 30px; margin: 22px 0; }
.pi-stat { display: flex; flex-direction: column; align-items: center; }
.pi-stat-num { font-size: 26px; font-weight: 800; color: #5a7d96; }
.pi-stat-lbl { font-size: 12px; color: #aaa; margin-top: 2px; }
.pi-count { font-size: 13px; color: #666; margin: 16px 0; }
.pi-actions { display: flex; gap: 12px; justify-content: center; margin-top: 8px; }

/* ===== 逐题作答 ===== */
.session-card {
  max-width: 560px; margin: 0 auto;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 22px 24px 26px;
  animation: session-in 0.25s ease;
}
@keyframes session-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.session-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sh-count { font-size: 20px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.sh-count i { font-style: normal; font-size: 13px; color: #b0b0b0; font-weight: 600; }
.sh-score { font-size: 13px; color: #5a7d96; font-weight: 700; }
.combo-badge {
  padding: 3px 12px; border-radius: 12px;
  background: linear-gradient(135deg, #f0975c, #e67e22);
  color: #fff; font-size: 12px; font-weight: 700;
  animation: combo-pop 0.3s ease;
}
@keyframes combo-pop { 0% { transform: scale(0.6); } 60% { transform: scale(1.15); } 100% { transform: scale(1); } }
.progress-bar { height: 6px; border-radius: 3px; background: rgba(0,0,0,0.06); margin-bottom: 20px; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #7c9db5, #5a7d96); transition: width 0.4s ease; }

.session-question {
  padding: 20px 22px; border-radius: 16px;
  border: 1.5px solid #ececec; background: rgba(250,249,246,0.7);
  transition: all 0.3s;
}
.session-question.correct { border-color: #c3e6cb; background: rgba(39,174,96,0.05); }
.session-question.wrong { border-color: #f0c4c4; background: rgba(231,76,60,0.04); }
.sq-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.sq-type {
  font-size: 11px; padding: 3px 12px; border-radius: 12px;
  background: rgba(90,125,150,0.1); color: #5a7d96; font-weight: 700;
}
.sq-text { font-size: 17px; color: var(--color-text); line-height: 1.7; font-weight: 500; }
.sq-hint { font-size: 12px; color: #999; margin-top: 8px; font-style: italic; }
.sq-row { display: flex; gap: 10px; margin-top: 16px; }
.sq-input {
  flex: 1; padding: 12px 16px; border-radius: 12px;
  border: 1.5px solid #e0e0e0; background: #fff; font-size: 16px;
  color: var(--color-text); outline: none; transition: border-color 0.2s;
}
.sq-input:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.1); }
.sq-input:disabled { background: #f5f5f5; color: #999; }
.fb-ok { color: #27ae60; font-size: 15px; font-weight: 700; margin-top: 12px; }
.fb-bad { margin-top: 12px; }
.fb-wrong { color: #c0392b; font-size: 14px; }
.fb-wrong strong { color: #27ae60; }
.fb-explain { display: block; color: #888; font-size: 13px; margin-top: 6px; line-height: 1.6; }
.session-actions { display: flex; justify-content: flex-end; margin-top: 20px; }
.kb-hint { text-align: center; font-size: 12px; color: #c0c0c0; margin-top: 14px; }

/* ===== 结果页 ===== */
.result-card {
  max-width: 520px; margin: 0 auto;
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06);
  padding: 32px 30px; text-align: center;
  animation: session-in 0.25s ease;
}
.res-ring {
  width: 120px; height: 120px; margin: 0 auto 18px;
  border-radius: 50%;
  background: conic-gradient(#5a7d96 calc(var(--p) * 1%), rgba(0,0,0,0.06) 0);
  display: flex; align-items: center; justify-content: center;
}
.res-ring-inner {
  width: 88px; height: 88px; border-radius: 50%;
  background: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.res-num { font-size: 24px; font-weight: 800; color: #5a7d96; }
.res-lbl { font-size: 11px; color: #aaa; }
.res-title { font-size: 20px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.res-stats { display: flex; justify-content: center; gap: 28px; margin: 20px 0; }
.res-stat { font-size: 13px; color: #888; display: flex; flex-direction: column; align-items: center; }
.res-n { font-size: 22px; font-weight: 800; color: var(--color-text); }
.res-wrong { text-align: left; margin: 8px 0 18px; padding: 14px 18px; background: rgba(231,76,60,0.04); border: 1px solid rgba(231,76,60,0.12); border-radius: 12px; }
.res-wrong h4 { font-size: 14px; font-weight: 700; color: #c0392b; margin-bottom: 8px; }
.res-wrong-item { margin-bottom: 10px; }
.res-wrong-item:last-child { margin-bottom: 0; }
.rw-q { font-size: 14px; color: #555; line-height: 1.6; }
.rw-a { font-size: 13px; color: #888; margin-top: 2px; }
.rw-a strong { color: #27ae60; }
.res-actions { display: flex; gap: 12px; justify-content: center; }

.empty-practice { text-align: center; padding: 40px 0; color: var(--color-text-muted); }
.empty-practice p { margin-bottom: 16px; font-size: 14px; }
.ai-badge {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; background: rgba(155,89,182,0.06);
  border: 1px solid rgba(155,89,182,0.15); border-radius: 10px;
  margin-top: 16px; font-size: 13px; color: #8e44ad;
}

/* ===== 长难句 ===== */
.daily-card {
  background: linear-gradient(135deg, rgba(124,157,181,0.08), rgba(90,125,150,0.04));
  border: 1.5px solid rgba(124,157,181,0.2); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.05);
  padding: 24px 28px; margin-bottom: 24px;
}
.daily-badge-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.daily-badge { font-size: 13px; font-weight: 700; color: #5a7d96; }
.daily-actions { display: flex; gap: 8px; align-items: center; }
.daily-counter { font-size: 12px; color: #999; min-width: 44px; text-align: center; }
.speak-btn {
  width: 30px; height: 30px; border-radius: 50%;
  border: 1.5px solid #d8d8d8; background: rgba(255,255,255,0.8);
  font-size: 14px; cursor: pointer; transition: all 0.2s;
  display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
  padding: 0 !important;
}
.speak-btn:hover { border-color: #7c9db5; background: rgba(124,157,181,0.1); }
.speak-btn.speaking { border-color: #27ae60; background: rgba(39,174,96,0.12); animation: speak-pulse 1s infinite; }
@keyframes speak-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.55; } }
.daily-sentence { font-size: 18px; font-weight: 600; color: var(--color-text); line-height: 1.8; margin-bottom: 10px; }
.daily-translation { font-size: 14px; color: #666; line-height: 1.7; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid rgba(124,157,181,0.3); }
.daily-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.daily-analysis { margin-top: 12px; }
.daily-analysis summary { font-size: 13px; color: #5a7d96; cursor: pointer; font-weight: 600; }
.grammar-tag { display: inline-block; padding: 3px 10px; border-radius: 100px; background: rgba(90,125,150,0.08); color: #5a7d96; font-size: 11px; font-weight: 600; }

.sentence-list { display: flex; flex-direction: column; gap: 12px; }
.sentence-card {
  background: rgba(255,255,255,0.82); backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.06); overflow: hidden;
}
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
</style>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useGrammarStore } from '@/stores/grammar'
import { useAuthStore } from '@/stores/auth'
import { getExamLevels, getLevelLabel } from '@/data/examLevels'
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

// 当前全局等级标签
const currentLevelLabel = computed(() => {
  if (authStore.targetLevel === -1 || authStore.targetLevel === null) return '全部等级'
  return getLevelLabel(store.lang, authStore.targetLevel)
})

function parseGrammar(raw) {
  if (!raw) return []
  try { return JSON.parse(raw) }
  catch { return raw.replace(/[\[\]"]/g, '').split(',').map(s => s.trim()).filter(Boolean) }
}

function check(q) {
  const ans = (q.userAnswer || '').trim()
  if (!ans) return
  store.checkAnswer(q, ans)
}

onMounted(() => {
  if (!store.lessons.length) store.fetchLessons()
  if (!store.sentenceList.length) store.fetchSentences()
  store.fetchStats()
})

// 切换 tab 时自动加载对应全局等级的内容
watch(() => store.tab, (tab) => {
  if (tab === 'practice') {
    store.fetchPractices(authStore.targetLevel)
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
      <button :class="{ active: store.tab === 'learn' }" @click="store.tab = 'learn'">语法教程</button>
      <button :class="{ active: store.tab === 'practice' }" @click="store.tab = 'practice'">分级练习</button>
      <button :class="{ active: store.tab === 'sentences' }" @click="store.tab = 'sentences'">长难句</button>
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

    <!-- ==================== 分级练习 ==================== -->
    <div v-if="store.tab === 'practice'">
      <div class="level-info-bar">
        <span class="level-info-label">当前等级</span>
        <span class="level-info-value">{{ currentLevelLabel }}</span>
        <button class="level-refresh" @click="store.fetchPractices(authStore.targetLevel)">刷新</button>
      </div>
      <div class="progress-info" v-if="store.totalQuestions">
        已完成 {{ store.answeredCount }}/{{ store.totalQuestions }}，正确 {{ store.correctCount }}
      </div>

      <LoadingSpinner v-if="store.loading.practices" />

      <div v-else-if="store.currentPractices.length > 0" class="question-block">
        <h3 class="block-title">
          {{ store.currentPractices[0]?.type === 'fill' ? '填空题' : '纠错题' }}
        </h3>
        <div v-for="q in store.currentPractices" :key="q.id" class="q-item"
          :class="{
            done: store.practiceAnswers[q.id] !== undefined,
            correct: store.practiceAnswers[q.id]?.isCorrect,
            wrong: store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect
          }">
          <p class="q-prompt">{{ q.question }}</p>
          <p v-if="q.hint" class="q-hint">{{ q.hint }}</p>
          <div class="q-row">
            <input
              v-model="q.userAnswer"
              class="q-input"
              :placeholder="q.type === 'fill' ? '输入答案...' : '输入正确形式...'"
              :disabled="store.practiceAnswers[q.id] !== undefined"
              @keyup.enter="check(q)"
            />
            <button
              v-if="store.practiceAnswers[q.id] === undefined"
              class="check-btn"
              @click="check(q)"
            >✓</button>
          </div>
          <p v-if="store.practiceAnswers[q.id]?.isCorrect" class="feedback-ok">正确!</p>
          <div v-if="store.practiceAnswers[q.id] !== undefined && !store.practiceAnswers[q.id]?.isCorrect" class="feedback-bad">
            错误! 正确答案: <strong>{{ q.answer }}</strong>
            <span v-if="q.explanation" class="explanation"> — {{ q.explanation }}</span>
          </div>
        </div>
        <button class="reset-btn" @click="store.resetPractices()">重做本组</button>
      </div>
      <p v-else class="empty-text">点击上方级别加载练习题</p>
    </div>

    <!-- ==================== 长难句精析 ==================== -->
    <div v-if="store.tab === 'sentences'">
      <div v-if="store.dailySentence" class="daily-card">
        <div class="daily-badge">
          每日一句 · {{ store.dailySentence.level === 'Advanced' ? '高级' : store.dailySentence.level === 'Intermediate' ? '中级' : '' }}
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

      <div class="filter-bar">
        <button :class="{ active: store.sentenceLevel === 'Intermediate' }" @click="store.setSentenceLevel('Intermediate')">中级</button>
        <button :class="{ active: store.sentenceLevel === 'Advanced' }" @click="store.setSentenceLevel('Advanced')">高级</button>
      </div>

      <div v-if="store.sentenceList.length > 0" class="sentence-list">
        <div v-for="s in store.sentenceList" :key="s.id" class="sentence-card"
          :class="{ expanded: expandedSid === s.id }">
          <div class="sentence-header" @click="expandedSid = expandedSid === s.id ? null : s.id">
            <div class="sentence-top-row">
              <span class="sentence-level-tag">{{ s.level === 'Advanced' ? '高级' : '中级' }}</span>
              <span v-if="s.source" class="sentence-source">{{ s.source }}</span>
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
        icon=""
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

.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 20px 0; }
.tab-bar button { padding: 10px 24px; border-radius: 20px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s; color: #666; }
.tab-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }

.stats-bar { text-align: center; font-size: 12px; color: #5a7d96; font-weight: 600; margin-bottom: 12px; padding: 6px 16px; background: rgba(90,125,150,0.05); border-radius: 10px; display: inline-block; width: 100%; }
.stats-div { margin: 0 10px; opacity: 0.3; }

.learn-area { display: flex; flex-direction: column; gap: 14px; }
.lesson-card { background: rgba(255,255,255,0.8); backdrop-filter: blur(12px); border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg); overflow: hidden; }
.lesson-header { padding: 16px 22px; font-size: 17px; font-weight: 700; color: var(--color-text); cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.lesson-header:hover { background: rgba(0,0,0,0.02); }
.expand-icon { font-size: 14px; color: #aaa; }
.lesson-body { padding: 0 22px 20px; }
.lesson-section { margin-top: 16px; padding: 14px 18px; background: #f8fafb; border-radius: 10px; border: 1px solid #f0f0f0; }
.lesson-subtitle { font-size: 14px; font-weight: 700; color: #5a7d96; margin-bottom: 6px; }
.lesson-text { font-size: 14px; color: #555; line-height: 1.8; white-space: pre-wrap; }

.level-bar { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-bottom: 8px; }
.level-bar button { padding: 8px 18px; border-radius: 18px; border: 1.5px solid #ddd; background: rgba(255,255,255,0.6); font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; color: #666; display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 90px; }
.level-bar button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.06); }
.level-exam-label { font-size: 16px; font-weight: 800; }
.level-name { font-size: 11px; font-weight: 500; opacity: 0.8; }
.progress-info { text-align: center; font-size: 13px; color: #5a7d96; font-weight: 600; margin-bottom: 18px; }

.question-block { margin-bottom: 24px; }
.block-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin-bottom: 4px; }
.q-item { padding: 14px 18px; margin-bottom: 12px; border-radius: 12px; border: 1px solid #eee; background: #fafafa; transition: all 0.3s; }
.q-item.done.correct { border-color: #d4edda; background: #f6fdf7; }
.q-item.done.wrong { border-color: #f8d7da; background: #fef9f9; }
.q-prompt { font-size: 15px; color: var(--color-text); margin-bottom: 4px; font-weight: 500; }
.q-hint { font-size: 12px; color: #888; margin-bottom: 8px; }
.q-row { display: flex; gap: 8px; }
.q-input { flex: 1; padding: 9px 14px; border-radius: 10px; border: 1.5px solid #e0e0e0; background: #fff; font-size: 14px; color: var(--color-text); outline: none; }
.q-input:focus { border-color: #7c9db5; box-shadow: 0 0 0 3px rgba(124,157,181,0.1); }
.check-btn { padding: 9px 16px; border-radius: 10px; border: none; background: #5a7d96; color: #fff; font-size: 16px; cursor: pointer; }
.feedback-ok { color: #27ae60; font-size: 13px; font-weight: 600; margin-top: 6px; }
.feedback-bad { color: #c0392b; font-size: 13px; margin-top: 6px; line-height: 1.5; }
.feedback-bad strong { color: #27ae60; }
.explanation { color: #888; font-size: 12px; }
.reset-btn { display: block; margin: 20px auto; padding: 10px 28px; border-radius: 10px; border: 1.5px solid #ddd; background: #fff; color: #666; font-size: 14px; cursor: pointer; }
.reset-btn:hover { background: #f5f5f5; }

.daily-card { background: linear-gradient(135deg, rgba(124,157,181,0.08), rgba(90,125,150,0.04)); border: 1.5px solid rgba(124,157,181,0.2); border-radius: var(--radius-lg); padding: 24px 28px; margin-bottom: 24px; }
.daily-badge { font-size: 13px; font-weight: 700; color: #5a7d96; margin-bottom: 12px; }
.daily-sentence { font-size: 18px; font-weight: 600; color: var(--color-text); line-height: 1.8; margin-bottom: 10px; }
.daily-translation { font-size: 14px; color: #666; line-height: 1.7; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid rgba(124,157,181,0.3); }
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

.level-info-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: rgba(255,255,255,0.6);
  border-radius: 12px;
}
.level-info-label { font-size: 13px; color: #888; }
.level-info-value { font-size: 14px; font-weight: 700; color: #5a7d96; padding: 4px 12px; background: rgba(90,125,150,0.1); border-radius: 8px; }
.level-refresh { padding: 4px 12px; border-radius: 8px; border: 1.5px solid #ddd; background: transparent; font-size: 12px; color: #666; cursor: pointer; }
.level-refresh:hover { border-color: #5a7d96; color: #5a7d96; }

.lesson-video { margin-top: 12px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.video-link {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px; background: rgba(251,114,153,0.06);
  border: 1px solid rgba(251,114,153,0.2); color: #fb7299;
  font-size: 13px; font-weight: 600; text-decoration: none;
  transition: all 0.2s;
}
.video-link:hover { background: #fb7299; color: #fff; border-color: #fb7299; }
</style>

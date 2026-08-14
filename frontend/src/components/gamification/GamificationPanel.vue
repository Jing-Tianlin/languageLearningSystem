<script setup>
/**
 * GamificationPanel.vue — 游戏化激励面板
 * 连胜火焰 + 今日目标进度环 + 成就徽章
 * 数据源: GET /stats/overview
 */
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'

const authStore = useAuthStore()
const BASE = API_BASE_URL

const stats = ref({
  totalWords: 0, masteredWords: 0, streak: 0,
  todayStudied: 0, dueCount: 0, wrongCount: 0,
  totalReviews: 0,
})
const loading = ref(false)

// 每日目标（默认 10，localStorage 可覆盖）
const todayGoal = ref(Number(localStorage.getItem('dailyGoal') || 10))

const goalPercent = computed(() => {
  if (todayGoal.value <= 0) return 0
  return Math.min(100, Math.round((stats.value.todayStudied / todayGoal.value) * 100))
})
const goalDone = computed(() => stats.value.todayStudied >= todayGoal.value)
// 进度环 SVG 周长
const RING_C = 2 * Math.PI * 26
const ringOffset = computed(() => RING_C * (1 - goalPercent.value / 100))

const streakTier = computed(() => {
  const s = stats.value.streak
  if (s >= 30) return { label: '炉火纯青', icon: 'fire' }
  if (s >= 7) return { label: '持之以恒', icon: 'fire' }
  if (s >= 3) return { label: '初露锋芒', icon: 'fire' }
  return { label: '再接再厉', icon: 'sparkles' }
})

// 成就徽章定义（前端基于 overview 计算解锁状态）
const badges = computed(() => {
  const s = stats.value
  return [
    { id: 'first', name: '初出茅庐', desc: '学习第一个单词', icon: 'sprout', done: s.totalWords >= 1 },
    { id: 'm10', name: '词汇新秀', desc: '掌握 10 个词', icon: 'book', done: s.masteredWords >= 10 },
    { id: 'm50', name: '词汇达人', desc: '掌握 50 个词', icon: 'medal', done: s.masteredWords >= 50 },
    { id: 'm100', name: '词汇大师', desc: '掌握 100 个词', icon: 'crown', done: s.masteredWords >= 100 },
    { id: 's3', name: '三天打鱼', desc: '连续学习 3 天', icon: 'wave', done: s.streak >= 3 },
    { id: 's7', name: '持之以恒', desc: '连续学习 7 天', icon: 'bolt', done: s.streak >= 7 },
    { id: 's30', name: '铁杆学员', desc: '连续学习 30 天', icon: 'fire', done: s.streak >= 30 },
    { id: 'r50', name: '温故知新', desc: '累计复习 50 次', icon: 'refresh', done: s.totalReviews >= 50 },
    { id: 'today', name: '今日事毕', desc: '完成今日目标', icon: 'check', done: goalDone.value },
  ]
})
const unlockedCount = computed(() => badges.value.filter(b => b.done).length)

async function loadStats() {
  if (!authStore.isLoggedIn) return
  loading.value = true
  try {
    const json = await fetchJson(`${BASE}/stats/overview`)
    if (json.code === 200 && json.data) {
      stats.value = { ...stats.value, ...json.data }
    }
  } catch (e) { /* 静默 */ }
  finally { loading.value = false }
}

function setDailyGoal() {
  const input = window.prompt('设置每日学习目标（个词）：', String(todayGoal.value))
  const n = parseInt(input, 10)
  if (!isNaN(n) && n > 0 && n <= 500) {
    todayGoal.value = n
    localStorage.setItem('dailyGoal', String(n))
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="gamification" :class="{ loading }">
    <!-- 三格指标卡 -->
    <div class="gam-cards">
      <div class="gam-card streak-card">
        <span class="streak-icon icon-svg" :class="streakTier.icon" />
        <div class="gam-main">
          <div class="streak-num">{{ stats.streak }}</div>
          <div class="streak-label">连续学习天数</div>
        </div>
        <div class="streak-tier" v-if="stats.streak > 0">{{ streakTier.label }}</div>
        <div class="streak-hint" v-else>今天开始学习，开启连胜！</div>
      </div>

      <div class="gam-card goal-card">
        <div class="goal-ring-wrap">
          <svg class="goal-ring" viewBox="0 0 64 64">
            <circle class="goal-ring-bg" cx="32" cy="32" r="26" />
            <circle class="goal-ring-fill" cx="32" cy="32" r="26"
              :stroke-dasharray="RING_C" :stroke-dashoffset="ringOffset" />
          </svg>
          <div class="goal-center">
            <span class="goal-num">{{ stats.todayStudied }}</span>
            <span class="goal-div">/</span>
            <span class="goal-target">{{ todayGoal }}</span>
          </div>
        </div>
        <div class="goal-text">
          <span>今日目标</span>
          <button class="goal-edit" title="修改目标" @click="setDailyGoal"><span class="icon-svg pencil" /></button>
        </div>
        <div class="goal-done" v-if="goalDone"><span class="icon-svg check goal-check" /> 今日目标达成</div>
      </div>

      <div class="gam-card due-card">
        <div class="due-num">{{ stats.dueCount }}</div>
        <div class="due-label">待复习词汇</div>
        <div class="due-hint">{{ stats.dueCount > 0 ? '按遗忘曲线该复习了' : '暂无到期复习' }}</div>
      </div>
    </div>

    <!-- 成就徽章 -->
    <div class="badge-section">
      <div class="badge-head">
        <span class="badge-title">成就徽章</span>
        <span class="badge-count">{{ unlockedCount }} / {{ badges.length }}</span>
      </div>
      <div class="badge-grid">
        <div v-for="b in badges" :key="b.id" class="badge-item" :class="{ locked: !b.done }" :title="b.desc">
          <span class="badge-icon icon-svg" :class="b.done ? b.icon : 'lock'" />
          <span class="badge-name">{{ b.name }}</span>
          <span class="badge-desc">{{ b.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gamification { display: flex; flex-direction: column; gap: 16px; }
.gamification.loading { opacity: 0.6; }
.gam-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.gam-card {
  background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(16px);
  border: 1px solid rgba(0, 0, 0, 0.06); border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.05);
  padding: 20px; position: relative; min-height: 108px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
/* 连胜 */
.streak-card { text-align: center; }
.streak-icon { font-size: 30px; line-height: 1; }
.streak-num { font-size: 34px; font-weight: 800; color: #c2622e; font-family: var(--font-heading); line-height: 1.1; }
.streak-label { font-size: 12px; color: #888; margin-top: 2px; }
.streak-tier { margin-top: 6px; font-size: 12px; font-weight: 700; color: #b07c4f; background: rgba(176, 124, 79, 0.1); padding: 2px 10px; border-radius: 100px; }
.streak-hint { margin-top: 6px; font-size: 12px; color: #aaa; }
/* 今日目标 */
.goal-card { padding-top: 16px; }
.goal-ring-wrap { position: relative; width: 64px; height: 64px; flex-shrink: 0; }
.goal-ring { width: 64px; height: 64px; transform: rotate(-90deg); }
.goal-ring-bg { fill: none; stroke: rgba(110, 122, 107, 0.15); stroke-width: 5; }
.goal-ring-fill { fill: none; stroke: #6e7a6b; stroke-width: 5; stroke-linecap: round; transition: stroke-dashoffset 0.6s ease; }
.goal-center { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; gap: 2px; }
.goal-num { font-size: 18px; font-weight: 800; color: #6e7a6b; }
.goal-div { font-size: 12px; color: #bbb; }
.goal-target { font-size: 12px; color: #999; }
.goal-text { margin-top: 8px; font-size: 12px; color: #888; display: flex; align-items: center; gap: 6px; }
.goal-edit { border: none; background: none; cursor: pointer; color: #b07c4f; font-size: 13px; padding: 0; display: inline-flex; }
.goal-edit .icon-svg::after { background: #b07c4f; }
.goal-done { margin-top: 4px; font-size: 12px; font-weight: 700; color: #6e7a6b; display: inline-flex; align-items: center; gap: 5px; animation: goal-pop 0.4s ease; }
.goal-check { font-size: 13px; }
@keyframes goal-pop { 0% { transform: scale(0.6); opacity: 0; } 60% { transform: scale(1.15); } 100% { transform: scale(1); opacity: 1; } }
/* 待复习 */
.due-card { text-align: center; }
.due-num { font-size: 34px; font-weight: 800; color: #5a7d96; font-family: var(--font-heading); line-height: 1.1; }
.due-label { font-size: 12px; color: #888; margin-top: 2px; }
.due-hint { margin-top: 6px; font-size: 12px; color: #aaa; }
/* 徽章 */
.badge-section { background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(16px); border: 1px solid rgba(0, 0, 0, 0.06); border-radius: 20px; box-shadow: 0 10px 40px rgba(0, 0, 0, 0.05); padding: 20px 22px; }
.badge-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.badge-title { font-size: 15px; font-weight: 800; color: var(--color-text); font-family: var(--font-heading); }
.badge-count { font-size: 12px; color: #999; font-weight: 600; }
.badge-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(92px, 1fr)); gap: 12px; }
.badge-item { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 2px; padding: 12px 6px; border-radius: 14px; background: rgba(110, 122, 107, 0.05); transition: all 0.2s; }
.badge-item.locked { background: rgba(0, 0, 0, 0.025); opacity: 0.55; filter: grayscale(0.6); }
.badge-item:not(.locked):hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08); }
.badge-icon { font-size: 24px; line-height: 1.2; }
.badge-name { font-size: 12px; font-weight: 700; color: var(--color-text); }
.badge-desc { font-size: 10px; color: #aaa; }
</style>

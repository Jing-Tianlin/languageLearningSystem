<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import LearningRoadmap from '@/components/cards/LearningRoadmap.vue'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'

const languageStore = useLanguageStore()
const authStore = useAuthStore()
const router = useRouter()
const BASE = API_BASE_URL

const activeSection = ref(0)

// 学习统计
const stats = ref({ totalWords: 0, masteredWords: 0, masteryRate: 0, totalReviews: 0, avgHesitationMs: 0, studyStreak: 0 })
const statsLoading = ref(false)

// 首页推荐
const homeRecs = ref([])
const homeRecLoading = ref(false)

async function loadHomeRecs() {
  if (!authStore.isLoggedIn || !authStore.user) return
  homeRecLoading.value = true
  const uid = authStore.user.id
  const lang = authStore.targetLanguage || 'en'
  const level = authStore.targetLevel
  const levelNames = ['小学', '初中', '高中', 'CET4', 'CET6', '专业人士']
  const levelLabel = level !== null && level !== -1 && levelNames[level] ? levelNames[level] : ''

  try {
    const res = await fetch(`${BASE}/ai/generate-reading`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ lang, level: level ?? 2, topic: '一篇适合' + levelLabel + '学习者的阅读文章' }),
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      homeRecs.value = [{
        title: json.data.title,
        content: json.data.content,
        level: json.data.level,
        coreVocabulary: JSON.stringify(json.data.coreVocabulary || []),
        quizQuestions: JSON.stringify(json.data.quizQuestions || []),
      }]
    }
  } catch (e) { homeRecs.value = [] }
  finally { homeRecLoading.value = false }
}

function goHomeReading() {
  if (homeRecs.value.length === 0) return
  const item = homeRecs.value[0]
  sessionStorage.setItem('aiReadingArticle', JSON.stringify(item))
  router.push('/reading?ai=1')
}

const currentLevelLabel = computed(() => getLevelLabel(authStore.targetLanguage, authStore.targetLevel))

async function loadStats() {
  if (!authStore.isLoggedIn || !authStore.user) return
  statsLoading.value = true
  try {
    const res = await fetch(`${BASE}/stats/overview?userId=${authStore.user.id}`)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      stats.value = json.data
    }
  } catch (e) { /* 静默 */ }
  finally { statsLoading.value = false }
}

onMounted(() => {
  languageStore.fetchLanguages()
  if (authStore.isLoggedIn) {
    authStore.fetchProfile()
    loadStats()
    loadHomeRecs()
  }
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function onScroll() {
  const h = window.innerHeight
  const scrollY = window.scrollY
  const idx = Math.round(scrollY / h)
  if (idx >= 0 && idx < 3) activeSection.value = idx
}

function scrollTo(index) {
  window.scrollTo({ top: index * window.innerHeight, behavior: 'smooth' })
  activeSection.value = index
}
</script>

<template>
  <div class="fullpage-wrapper">

    <!-- ===== 右侧导航点 ===== -->
    <div class="nav-dots">
      <div
        v-for="i in 3" :key="i"
        class="nav-dot"
        :class="{ active: activeSection === i - 1 }"
        @click="scrollTo(i - 1)"
      />
    </div>

    <!-- ===== Section 1: Hero ===== -->
    <section class="fp-section fp-hero">
      <div class="fp-content">
        <div class="hero-badge">
          <span class="badge-dot" /> 创新的语言学习方式
        </div>
        <LetterSwapTitle text="探索语言之美" tag="h1" color="#5a7d96" font-size="56px" :font-weight="700" letter-spacing="-0.5px" />
        <p class="hero-desc">
          沉浸式多语言学习体验，通过 AI 驱动的个性化课程<br />
          让每一门语言都触手可及
        </p>
        <router-link to="/learn" class="cta-primary">
          开始学习
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
        </router-link>
        <p class="scroll-hint">↓ 向下滚动了解更多</p>
      </div>
    </section>

    <!-- ===== Section 2: 学习路径 ===== -->
    <section class="fp-section fp-roadmap">
      <div class="fp-content">
        <h2 class="section-title">学习路径</h2>
        <p class="section-sub">语言学习的7个必经步骤</p>
        <LearningRoadmap lang-code="en" />
      </div>
    </section>

    <!-- ===== Section 3: 学习概览 ===== -->
    <section class="fp-section fp-stats">
      <div class="fp-content">
        <h2 class="section-title">学习概览</h2>

        <div v-if="authStore.isLoggedIn" class="stats-area">
          <!-- 核心指标 -->
          <div class="stats-hero">
            <div class="sh-card primary">
              <span class="sh-num">{{ stats.totalWords || 0 }}</span>
              <span class="sh-lbl">学习词汇</span>
            </div>
            <div class="sh-card secondary">
              <span class="sh-num">{{ stats.masteredWords || 0 }}</span>
              <span class="sh-lbl">已掌握</span>
            </div>
            <div class="sh-card accent">
              <span class="sh-num">{{ stats.masteryRate || 0 }}%</span>
              <span class="sh-lbl">掌握率</span>
            </div>
          </div>

          <!-- 详情指标 -->
          <div class="stats-detail">
            <div class="sd-item">
              <span class="sd-val">{{ stats.totalReviews || 0 }} 次</span>
              <span class="sd-lbl">总复习</span>
            </div>
            <div class="sd-item">
              <span class="sd-val">{{ stats.avgHesitationMs || 0 }}ms</span>
              <span class="sd-lbl">平均犹豫时间</span>
            </div>
            <div class="sd-item">
              <span class="sd-val">{{ authStore.user?.totalStudyDays || 0 }} 天</span>
              <span class="sd-lbl">连续学习</span>
            </div>
            <div class="sd-item">
              <span class="sd-val">{{ currentLevelLabel }}</span>
              <span class="sd-lbl">当前等级</span>
            </div>
          </div>

          <!-- 快捷操作 -->
          <!-- 首页推荐 -->
          <div class="home-rec" v-if="authStore.isLoggedIn">
            <div class="hr-header">
              <h3>今日推荐阅读</h3>
              <button class="hr-refresh" :disabled="homeRecLoading" @click="loadHomeRecs">
                {{ homeRecLoading ? '刷新中...' : '刷新' }}
              </button>
            </div>
            <div v-if="homeRecs.length" class="hr-card" @click="goHomeReading">
              <span class="hr-title">{{ homeRecs[0].title }}</span>
              <span class="hr-hint">点击开始阅读 →</span>
            </div>
            <p v-if="homeRecLoading" class="hr-loading">AI 正在生成推荐文章...</p>
          </div>

          <div class="stats-actions">
            <router-link to="/flashcards" class="sa-btn">背单词</router-link>
            <router-link to="/practice" class="sa-btn">每日练习</router-link>
            <router-link to="/stats" class="sa-btn">详细分析</router-link>
          </div>
        </div>

        <div v-else class="empty-login">
          登录后查看学习概览
          <router-link to="/login" class="login-link">去登录 →</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* === 全屏滚动容器 === */
.fullpage-wrapper { position: relative; }

/* === 每个 Section 占满整屏 === */
.fp-section {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}
.fp-content {
  width: 100%;
  max-width: 1100px;
  text-align: center;
  animation: fadeIn 0.6s ease;
}
@keyframes fadeIn { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }

/* === 右侧导航点 === */
.nav-dots {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1100;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.nav-dot {
  width: 12px; height: 12px;
  border-radius: 50%;
  background: rgba(0,0,0,0.15);
  cursor: pointer;
  transition: all 0.3s ease;
}
.nav-dot:hover { background: rgba(0,0,0,0.3); transform: scale(1.3); }
.nav-dot.active { background: var(--color-primary-dark); box-shadow: 0 0 8px rgba(90,125,150,0.4); }

/* === Section 1: Hero === */
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 16px; border-radius: var(--radius-full);
  background: rgba(255,255,255,0.6); backdrop-filter: blur(8px);
  border: 1px solid rgba(0,0,0,0.05);
  font-size: 13px; font-weight: 500; color: var(--color-text-secondary); margin-bottom: 24px;
}
.badge-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--color-primary-dark); animation: pulse-dot 2s ease-in-out infinite;
}
@keyframes pulse-dot { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.5;transform:scale(1.8)} }
.fp-hero :deep(.letter-swap-title) {
  font-size: 52px; font-weight: 900; color: var(--color-text);
  margin-bottom: 16px; letter-spacing: -1px; line-height: 1.15;
}
.hero-desc { font-size: 16px; color: var(--color-text-secondary); line-height: 1.7; margin-bottom: 28px; }
.cta-primary {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 16px 40px; border-radius: var(--radius-full);
  background: var(--color-primary-dark); color: #fff;
  font-size: 17px; font-weight: 700; text-decoration: none;
  transition: all 0.35s var(--ease-smooth);
  box-shadow: 0 4px 24px rgba(90,125,150,0.35);
}
.cta-primary:hover { transform: translateY(-2px); box-shadow: 0 8px 36px rgba(90,125,150,0.5); }
.scroll-hint { margin-top: 28px; font-size: 13px; color: #bbb; animation: bounce 2s infinite; }
@keyframes bounce { 0%,100%{transform:translateY(0)} 50%{transform:translateY(6px)} }

/* === Section 2 & 3 通用 === */
.section-title { font-size: 32px; font-weight: 800; color: var(--color-text); margin-bottom: 8px; font-family: var(--font-heading); letter-spacing: -0.5px; }
.section-sub { font-size: 15px; color: var(--color-text-secondary); margin-bottom: 36px; }

/* ===== 学习概览 ===== */
.stats-area { max-width: 720px; margin: 0 auto; }

.stats-hero { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 20px; }
.sh-card {
  padding: 28px 20px; border-radius: 16px; text-align: center;
  display: flex; flex-direction: column; gap: 4px;
}
.sh-card.primary { background: linear-gradient(135deg, rgba(124,157,181,0.1), rgba(90,125,150,0.05)); border: 1px solid rgba(90,125,150,0.1); }
.sh-card.secondary { background: linear-gradient(135deg, rgba(39,174,96,0.06), rgba(39,174,96,0.02)); border: 1px solid rgba(39,174,96,0.1); }
.sh-card.accent { background: linear-gradient(135deg, rgba(240,151,92,0.06), rgba(240,151,92,0.02)); border: 1px solid rgba(240,151,92,0.1); }
.sh-num { font-size: 36px; font-weight: 700; color: #333; letter-spacing: -1px; }
.sh-card.accent .sh-num { font-size: 28px; }
.sh-lbl { font-size: 13px; color: #888; font-weight: 500; }

.stats-detail { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 24px; }
.sd-item {
  padding: 16px 12px; border-radius: 12px; background: rgba(255,255,255,0.7);
  border: 1px solid rgba(0,0,0,0.04); text-align: center;
  display: flex; flex-direction: column; gap: 2px;
}
.sd-icon { font-size: 18px; }
.sd-val { font-size: 15px; font-weight: 700; color: #333; }
.sd-lbl { font-size: 11px; color: #aaa; }

.stats-actions { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }
.sa-btn {
  padding: 10px 22px; border-radius: 10px; border: 1.5px solid rgba(90,125,150,0.2);
  background: rgba(255,255,255,0.7); color: #5a7d96;
  font-size: 14px; font-weight: 600; text-decoration: none;
  transition: all 0.25s;
}
.sa-btn:hover { background: rgba(90,125,150,0.06); border-color: rgba(90,125,150,0.4); }
.empty-login { font-size: 16px; color: var(--color-text-muted); }
.login-link { color: var(--color-primary-dark); font-weight: 600; text-decoration: none; margin-left: 8px; }

@media (max-width: 768px) {
  .fp-hero :deep(.letter-swap-title) { font-size: 30px; }
  .section-title { font-size: 24px; }
  .nav-dots { right: 12px; gap: 12px; }
  .nav-dot { width: 10px; height: 10px; }
}

/* 首页推荐 */
.home-rec { margin-bottom: 20px; }
.hr-header {
  display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 10px;
}
.hr-header h3 { font-size: 16px; font-weight: 700; color: var(--color-text); margin: 0; }
.hr-refresh {
  padding: 4px 14px; border-radius: 6px; border: 1.5px solid rgba(90,125,150,0.2);
  background: rgba(255,255,255,0.6); color: #5a7d96; font-size: 12px; font-weight: 600; cursor: pointer;
  transition: all 0.2s;
}
.hr-refresh:hover:not(:disabled) { background: rgba(90,125,150,0.06); border-color: #5a7d96; }
.hr-refresh:disabled { opacity: 0.5; cursor: not-allowed; }
.hr-card {
  padding: 14px 20px; border-radius: 10px;
  background: linear-gradient(135deg, rgba(90,125,150,0.04), rgba(90,125,150,0.08));
  border: 1px solid rgba(90,125,150,0.1); cursor: pointer;
  display: flex; align-items: center; justify-content: space-between;
  transition: all 0.2s;
}
.hr-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(90,125,150,0.08); }
.hr-title { font-size: 15px; font-weight: 600; color: var(--color-text); }
.hr-hint { font-size: 13px; color: #5a7d96; font-weight: 500; }
.hr-loading { text-align: center; padding: 12px; color: #aaa; font-size: 13px; }
</style>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { getLevelLabel } from '@/data/examLevels'
import { API_BASE_URL } from '@/config'
import LearningRoadmap from '@/components/cards/LearningRoadmap.vue'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import GamificationPanel from '@/components/gamification/GamificationPanel.vue'

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

// 特色能力展示
const features = [
  { icon: 'vocab', title: '科学词库', desc: '分级词汇 + 艾宾浩斯遗忘曲线复习' },
  { icon: 'ai', title: 'AI 助手', desc: '智能对话、出题与个性化推荐' },
  { icon: 'speak', title: '听说训练', desc: '发音跟读、听音辨义、口语表达' },
  { icon: 'book', title: '沉浸阅读', desc: '分级阅读与 AI 生成短文' },
]

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
const userName = computed(() => authStore.user?.nickname || authStore.user?.username || '同学')

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

// ====== 全屏滚动 ======
const sectionCount = computed(() => (authStore.isLoggedIn ? 5 : 4))
const sectionEls = () => Array.from(document.querySelectorAll('.fp-section'))
const scrolling = ref(false) // 动画期间加锁，避免一次滚动连续跳多屏
let scrollTimer = null

function onScroll() {
  const h = window.innerHeight
  const idx = Math.round(window.scrollY / h)
  if (idx >= 0 && idx < sectionCount.value) activeSection.value = idx
}

function scrollTo(index) {
  jumpTo(index)
}

function onWheel(e) {
  if (scrolling.value) { e.preventDefault(); return }
  const secs = sectionEls()
  if (secs.length === 0) return
  // 当前屏内容高度超过视口时放行原生滚动，避免内容被截断
  const cur = secs[activeSection.value]
  if (cur && cur.scrollHeight > cur.clientHeight + 40) return
  if (Math.abs(e.deltaY) < 16) return
  const next = activeSection.value + (e.deltaY > 0 ? 1 : -1)
  if (next < 0 || next >= secs.length) return
  e.preventDefault()
  jumpTo(next)
}

function jumpTo(index) {
  scrolling.value = true
  activeSection.value = index
  const secs = sectionEls()
  const top = secs[index] ? secs[index].offsetTop : index * window.innerHeight
  window.scrollTo({ top, behavior: 'smooth' })
  clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => { scrolling.value = false }, 800)
}

onMounted(() => {
  languageStore.fetchLanguages()
  if (authStore.isLoggedIn) {
    authStore.fetchProfile()
    loadStats()
    loadHomeRecs()
  }
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('wheel', onWheel, { passive: false })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('wheel', onWheel)
  if (scrollTimer) clearTimeout(scrollTimer)
})
</script>

<template>
  <div class="fullpage-wrapper">
    <!-- ===== 右侧导航点 ===== -->
    <div class="nav-dots">
      <div
        v-for="i in sectionCount" :key="i"
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
        <LetterSwapTitle text="探索语言之美" tag="h1" color="#3e463b" font-size="54px" :font-weight="600" letter-spacing="-0.5px" />
        <p class="hero-desc">
          沉浸式多语言学习体验，通过 AI 驱动的个性化课程<br />
          让每一门语言都触手可及
        </p>
        <div class="hero-actions">
          <router-link to="/learn" class="btn btn-primary btn-lg">
            开始学习
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </router-link>
          <router-link to="/vocabulary" class="btn btn-ghost btn-lg">浏览词库</router-link>
        </div>
        <p class="scroll-hint">↓ 向下滚动了解更多</p>
      </div>
    </section>

    <!-- ===== Section 2: 特色能力 ===== -->
    <section class="fp-section fp-features">
      <div class="fp-content">
        <h2 class="section-title">核心能力</h2>
        <p class="section-sub">围绕记忆曲线与 AI，构建完整学习闭环</p>
        <div class="features">
          <div v-for="f in features" :key="f.title" class="feat">
            <span class="feat-icon icon-svg" :class="f.icon" />
            <div class="feat-text">
              <h3 class="feat-title">{{ f.title }}</h3>
              <p class="feat-desc">{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== Section 3: 学习路径 ===== -->
    <section class="fp-section fp-roadmap">
      <div class="fp-content">
        <h2 class="section-title">学习路径</h2>
        <p class="section-sub">语言学习的 7 个必经步骤，循序渐进</p>
        <LearningRoadmap lang-code="en" />
      </div>
    </section>

    <!-- ===== Section 4: 学习概览 ===== -->
    <section class="fp-section fp-overview">
      <div class="fp-content">
        <div v-if="authStore.isLoggedIn" class="stats-area">
          <!-- 欢迎行 -->
          <div class="welcome-row">
            <div class="welcome-text">
              <h2 class="welcome-title">Hi，{{ userName }}</h2>
              <p class="welcome-sub">今天也一起加油吧</p>
            </div>
            <div class="welcome-level">
              <span class="icon-svg target" />
              {{ currentLevelLabel }}
            </div>
          </div>

          <!-- 游戏化激励 + 核心指标：左右两栏，内容更紧凑 -->
          <div class="overview-grid">
            <div class="overview-left">
              <GamificationPanel />
            </div>
            <div class="overview-right">
              <div class="stats-hero">
                <div class="sh-card">
                  <span class="sh-icon icon-svg vocab" />
                  <div class="sh-body">
                    <span class="sh-num">{{ stats.totalWords || 0 }}</span>
                    <span class="sh-lbl">学习词汇</span>
                  </div>
                </div>
                <div class="sh-card">
                  <span class="sh-icon icon-svg book" />
                  <div class="sh-body">
                    <span class="sh-num">{{ stats.masteredWords || 0 }}</span>
                    <span class="sh-lbl">已掌握</span>
                  </div>
                </div>
                <div class="sh-card accent">
                  <span class="sh-icon icon-svg trophy" />
                  <div class="sh-body">
                    <span class="sh-num">{{ stats.masteryRate || 0 }}%</span>
                    <span class="sh-lbl">掌握率</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-login">
          登录后查看学习概览
          <router-link to="/login" class="login-link">去登录 →</router-link>
        </div>
      </div>
    </section>

    <!-- ===== Section 5: 学习详情（仅登录） ===== -->
    <section v-if="authStore.isLoggedIn" class="fp-section fp-detail">
      <div class="fp-content">
        <h2 class="section-title">学习详情</h2>
        <div class="stats-area">
          <div class="stats-detail">
            <div class="sd-item">
              <span class="sd-icon icon-svg refresh" />
              <span class="sd-val">{{ stats.totalReviews || 0 }} 次</span>
              <span class="sd-lbl">总复习</span>
            </div>
            <div class="sd-item">
              <span class="sd-icon icon-svg clock" />
              <span class="sd-val">{{ stats.avgHesitationMs || 0 }}ms</span>
              <span class="sd-lbl">平均犹豫时间</span>
            </div>
            <div class="sd-item">
              <span class="sd-icon icon-svg calendar" />
              <span class="sd-val">{{ stats.streak || 0 }} 天</span>
              <span class="sd-lbl">连续学习</span>
            </div>
            <div class="sd-item">
              <span class="sd-icon icon-svg flag" />
              <span class="sd-val">{{ currentLevelLabel }}</span>
              <span class="sd-lbl">当前等级</span>
            </div>
          </div>

          <div class="home-rec">
            <div class="hr-header">
              <h3><span class="icon-svg sparkles" /> 今日推荐阅读</h3>
              <button class="hr-refresh btn btn-ghost btn-sm" :disabled="homeRecLoading" @click="loadHomeRecs">
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
            <router-link to="/flashcards" class="sa-btn btn btn-secondary">背单词</router-link>
            <router-link to="/stats" class="sa-btn btn btn-secondary">详细分析</router-link>
          </div>
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
  align-items: center; /* Hero 垂直居中 */
  justify-content: center;
  padding: 2rem;
}
/* 其余分屏内容靠上对齐，避免小屏留白过大 */
.fp-features, .fp-roadmap {
  align-items: flex-start;
  padding: clamp(48px, 10vh, 120px) 2rem 4rem;
}
.fp-overview, .fp-detail {
  align-items: flex-start;
  padding: 36px 2rem 4rem;
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
  right: 28px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1100;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.nav-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  background: rgba(62, 54, 44, 0.16);
  cursor: pointer;
  transition: all 0.4s var(--ease-smooth);
}
.nav-dot:hover { background: rgba(62, 54, 44, 0.35); transform: scale(1.3); }
.nav-dot.active { background: var(--color-gold); box-shadow: 0 0 0 3px rgba(176, 124, 79, 0.15); }

/* === Hero === */
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 7px 16px; border-radius: var(--radius-full);
  background: rgba(255, 252, 247, 0.72); backdrop-filter: blur(8px);
  border: 1px solid var(--color-border);
  font-size: 12.5px; font-weight: 500; color: var(--color-text-secondary); margin-bottom: 30px;
  letter-spacing: 0.8px;
}
.badge-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: var(--color-gold); animation: pulse-dot 2.5s ease-in-out infinite;
}
@keyframes pulse-dot { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.5;transform:scale(1.8)} }
.fp-hero :deep(.letter-swap-title) {
  font-size: 54px; font-weight: 600; color: var(--color-text);
  font-family: var(--font-heading);
  margin-bottom: 22px; letter-spacing: -0.8px; line-height: 1.1;
}
.hero-desc { font-size: 16px; color: var(--color-text-secondary); line-height: 1.85; margin-bottom: 36px; }
.hero-actions { display: flex; justify-content: center; align-items: center; gap: 14px; flex-wrap: wrap; }
.hero-actions .btn { display: inline-flex; align-items: center; gap: 8px; }
.scroll-hint { margin-top: 42px; font-size: 13px; color: var(--color-text-muted); animation: bounce 2.5s infinite; }
@keyframes bounce { 0%,100%{transform:translateY(0)} 50%{transform:translateY(6px)} }

/* === 区块标题 === */
.section-title { font-size: 30px; font-weight: 600; color: var(--color-text); margin-bottom: 10px; font-family: var(--font-heading); letter-spacing: -0.5px; }
.section-sub { font-size: 14.5px; color: var(--color-text-muted); margin-bottom: 42px; letter-spacing: 0.6px; }

/* === 特色能力 === */
.features { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
.feat {
  display: flex; align-items: flex-start; gap: 14px;
  padding: 24px 20px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  text-align: left;
  transition: all 0.25s ease;
}
.feat:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: var(--color-border-hover); }
.feat-icon { font-size: 30px; flex-shrink: 0; margin-top: 2px; }
.feat-title { font-size: 15px; font-weight: 700; color: var(--color-text); margin: 0 0 4px; font-family: var(--font-heading); }
.feat-desc { font-size: 12.5px; color: var(--color-text-muted); line-height: 1.6; margin: 0; }

/* === 学习概览 / 详情 === */
.stats-area { max-width: 1040px; margin: 0 auto; }

/* 学习概览：左右两栏 */
.overview-grid {
  display: grid;
  grid-template-columns: 1.65fr 1fr;
  gap: 18px;
  align-items: start;
}
.overview-left, .overview-right { min-width: 0; }

/* 欢迎行 */
.welcome-row {
  display: flex; align-items: center; justify-content: space-between;
  text-align: left; margin-bottom: 20px; gap: 16px;
}
.welcome-title { font-size: 24px; font-weight: 700; color: var(--color-text); margin: 0; font-family: var(--font-heading); letter-spacing: -0.3px; }
.welcome-sub { font-size: 12.5px; color: var(--color-text-muted); margin: 3px 0 0; }
.welcome-level {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 16px; border-radius: var(--radius-full);
  background: rgba(176, 124, 79, 0.1);
  border: 1px solid rgba(176, 124, 79, 0.18);
  font-size: 13px; font-weight: 600; color: var(--color-gold);
  white-space: nowrap; flex-shrink: 0;
}

.stats-hero { display: flex; flex-direction: column; gap: 14px; }
.sh-card {
  padding: 22px 20px; border-radius: var(--radius-lg); text-align: left;
  display: flex; align-items: center; gap: 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  transition: all 0.25s ease;
}
.sh-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.sh-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 52px; height: 52px; border-radius: 14px;
  background: rgba(110, 122, 107, 0.1);
  font-size: 24px; flex-shrink: 0;
}
.sh-card.accent .sh-icon { background: rgba(176, 124, 79, 0.12); }
.sh-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.sh-num { font-size: 28px; font-weight: 600; color: var(--color-text); letter-spacing: -0.8px; font-family: var(--font-number); font-variant-numeric: tabular-nums; line-height: 1.2; }
.sh-card.accent .sh-num { color: var(--color-gold); }
.sh-lbl { font-size: 12.5px; color: var(--color-text-muted); font-weight: 500; letter-spacing: 0.5px; }

.stats-detail { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 26px; }
.sd-item {
  padding: 20px 12px; border-radius: var(--radius-md); background: var(--color-bg-card);
  border: 1px solid var(--color-border); text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  box-shadow: var(--shadow-xs);
  transition: all 0.25s ease;
}
.sd-item:hover { transform: translateY(-2px); box-shadow: var(--shadow-sm); }
.sd-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border-radius: 50%;
  background: rgba(110, 122, 107, 0.08);
  font-size: 16px; margin-bottom: 2px;
}
.sd-val { font-size: 15px; font-weight: 600; color: var(--color-text); font-family: var(--font-number); font-variant-numeric: tabular-nums; }
.sd-lbl { font-size: 11px; color: var(--color-text-muted); letter-spacing: 0.5px; }

.stats-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.sa-btn { text-decoration: none; }
.empty-login { font-size: 16px; color: var(--color-text-muted); }
.login-link { color: var(--color-primary); font-weight: 600; text-decoration: none; margin-left: 8px; }

/* 首页推荐 */
.home-rec { margin-bottom: 26px; }
.hr-header { display: flex; align-items: center; justify-content: center; gap: 14px; margin-bottom: 12px; }
.hr-header h3 { font-size: 16px; font-weight: 600; color: var(--color-text); margin: 0; font-family: var(--font-heading); letter-spacing: 0.3px; display: inline-flex; align-items: center; gap: 6px; }
.hr-refresh { color: var(--color-gold); }
.hr-refresh:hover:not(:disabled) { color: var(--color-gold); background: rgba(176, 124, 79, 0.05); }
.hr-refresh:disabled { opacity: 0.5; cursor: not-allowed; }
.hr-card {
  padding: 16px 22px; border-radius: var(--radius-md);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border); cursor: pointer;
  display: flex; align-items: center; justify-content: space-between;
  transition: all 0.25s;
  box-shadow: var(--shadow-xs);
}
.hr-card:hover { border-color: var(--color-border-hover); box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.hr-title { font-size: 15px; font-weight: 500; color: var(--color-text); }
.hr-hint { font-size: 13px; color: var(--color-gold); font-weight: 500; }
.hr-loading { text-align: center; padding: 14px; color: var(--color-text-muted); font-size: 13px; }

@media (max-width: 768px) {
  .fp-hero :deep(.letter-swap-title) { font-size: 34px; }
  .section-title { font-size: 26px; }
  .nav-dots { right: 14px; gap: 14px; }
  .nav-dot { width: 9px; height: 9px; }
  .features { grid-template-columns: repeat(2, 1fr); gap: 12px; }
  .feat { padding: 16px 14px; gap: 10px; }
  .feat-icon { font-size: 24px; }
  .stats-detail { grid-template-columns: repeat(2, 1fr); }
  .overview-grid { grid-template-columns: 1fr; }
  .stats-hero { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .sh-card { padding: 16px 14px; gap: 12px; }
  .sh-icon { width: 44px; height: 44px; font-size: 20px; border-radius: 12px; }
  .sh-num { font-size: 22px; }
}
</style>

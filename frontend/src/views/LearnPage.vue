<script setup>
/**
 * LearnPage.vue — 多语言学习中心
 *
 * 流程:
 * 1. 首次进入 / 未选语言 → 语言选择
 * 2. 已选语言 / 未选等级 → 等级选择
 * 3. 已选语言和等级 → 学习中心主页
 * 已选过的用户直接进入第 3 步
 */
import { ref, computed, onMounted } from 'vue'
import { useLanguageStore } from '@/stores/language'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { toast } from '@/composables/useToast'
import { getExamLevels, getExamName } from '@/data/examLevels'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import Skeleton from '@/components/common/Skeleton.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const languageStore = useLanguageStore()
const authStore = useAuthStore()

// 页面步骤: lang → level → center
const step = ref('lang')

const langCode = ref('')
const langName = ref('')
const loadingStats = ref(false)

// 统计数据
const langStats = ref({ vocabCount: 0, studiedCount: 0, masteredCount: 0 })

import { API_BASE_URL } from '@/config'

const flagIcons = { en: '🇬🇧', ja: '🇯🇵', ko: '🇰🇷', fr: '🇫🇷', de: '🇩🇪' }
const langDesc = {
  en: '从日常对话到商务英语，系统提升英语能力',
  ja: '从五十音到流利会话，轻松掌握日语',
  ko: '追剧学韩语，系统课程构建韩语基础',
  fr: '浪漫法语入门到精通的完整学习路径',
  de: '严谨的语法结构 + 高效词汇记忆法',
}

const BASE = API_BASE_URL

const examLevels = computed(() => getExamLevels(langCode.value || 'en'))
const examName = computed(() => getExamName(langCode.value || 'en'))

onMounted(async () => {
  await languageStore.fetchLanguages()

  const savedLang = authStore.targetLanguage
  const savedLevel = authStore.targetLevel

  if (savedLang && savedLevel !== null && savedLevel !== undefined) {
    // 语言和等级都选过，直接进入学习中心
    langCode.value = savedLang
    const found = languageStore.languages.find((l) => l.code === savedLang)
    langName.value = found ? found.nameCn : ''
    step.value = 'center'
    await loadLangStats()
  } else if (savedLang) {
    // 选了语言但没选等级，进入等级选择
    langCode.value = savedLang
    const found = languageStore.languages.find((l) => l.code === savedLang)
    langName.value = found ? found.nameCn : ''
    step.value = 'level'
  } else {
    // 什么都没选，从头开始
    step.value = 'lang'
  }
})

// ===== 选择语言 =====
async function selectLanguage(lang) {
  langCode.value = lang.code
  langName.value = lang.nameCn
  authStore.setTargetLanguage(lang.code)

  if (authStore.isLoggedIn && authStore.user) {
    authStore.user.currentLangCode = lang.code
    try {
      await userApi.updateUser({ id: authStore.user.id, currentLangCode: lang.code })
    } catch (e) { /* ignore */ }
  }

  // 进入等级选择
  step.value = 'level'
}

// ===== 选择等级 =====
function selectLevel(levelValue) {
  authStore.setTargetLevel(levelValue)
  step.value = 'center'
  loadLangStats()
  const lv = examLevels.value.find(l => l.value === levelValue)
  if (lv) {
    toast.success(`已选择 ${lv.examLabel} · ${lv.examName}`)
  }
}

// ===== 返回重新选择 =====
function reselectLang() {
  step.value = 'lang'
  langCode.value = ''
  langName.value = ''
  authStore.setTargetLanguage('')
  authStore.setTargetLevel(null)
}

function reselectLevel() {
  step.value = 'level'
}

// ===== 加载统计 =====
async function loadLangStats() {
  loadingStats.value = true
  const userId = authStore.user?.id || localStorage.getItem('userId')
  try {
    const [vocabRes, progressRes] = await Promise.all([
      fetch(`${BASE}/vocabulary/vocabularies?langCode=${langCode.value}&pageSize=1`).then(r => r.json()),
      fetch(`${BASE}/progress/progresses?userId=${userId}&langCode=${langCode.value}&pageSize=500`).then(r => r.json()),
    ])
    langStats.value.vocabCount = vocabRes.data?.total || 0
    const records = progressRes.data?.records || []
    langStats.value.studiedCount = records.filter(r => r.reviewCount > 0).length
    langStats.value.masteredCount = records.filter(r => r.masteryLevel >= 3).length
  } catch (e) {
    toast.error('统计数据加载失败')
  } finally {
    loadingStats.value = false
  }
}

// 当前等级标签
const currentLevelLabel = computed(() => {
  const lv = examLevels.value.find(l => l.value === authStore.targetLevel)
  return lv ? `${lv.examLabel} · ${lv.examName}` : '全部等级'
})

const learnModules = computed(() => [
  { icon: 'vocab', title: '词汇学习', desc: `积累 ${langName.value} 核心词汇，含音标例句发音`, to: '/vocabulary', stat: `${langStats.value.vocabCount} 词` },
  { icon: 'practice', title: '背单词·练', desc: '卡片/听音/拼写学习 + 巩固练习闭环', to: '/flashcards', stat: `${langStats.value.studiedCount} 已学` },
  { icon: 'grammar', title: '语法专项', desc: '时态 · 介词 · 冠词 · 语序专题突破', to: '/grammar' },
  { icon: 'reading', title: '阅读理解', desc: '三遍阅读法：速读→精读→答题', to: '/reading' },
  { icon: 'writing', title: '写作训练', desc: '仿写→连词成句→自由写作，逐级提升', to: '/writing' },
  { icon: 'link', title: '联动学习', desc: '热点词库 · 弱项语法 · 词性短缺智能检测', to: '/linkage' },
])
</script>

<template>
  <div class="page-wrap">

    <!-- ========== 语言选择 ========== -->
    <template v-if="step === 'lang'">
      <div class="page-header">
        <LetterSwapTitle text="选择你要学习的语言" tag="h1" />
        <p class="page-sub">选择语言后将进入等级选择，系统会为你推荐对应难度的内容</p>
      </div>

      <LoadingSpinner v-if="languageStore.loading" />

      <div v-else class="pick-grid">
        <div
          v-for="lang in languageStore.languages"
          :key="lang.id"
          class="pick-card"
          @click="selectLanguage(lang)"
        >
          <span class="pick-icon lang-pick-icon">{{ flagIcons[lang.code] || '🌍' }}</span>
          <div class="pick-body">
            <h4 class="pick-title">
              {{ lang.nameCn }}
              <i class="pick-native">{{ lang.nameNative }}</i>
            </h4>
            <p class="pick-desc">{{ langDesc[lang.code] || '系统学习这门语言，从入门到精通' }}</p>
          </div>
          <span class="pick-arrow">→</span>
        </div>
      </div>
    </template>

    <!-- ========== 等级选择 ========== -->
    <template v-if="step === 'level'">
      <div class="page-header">
        <div class="header-top">
          <div class="lang-badge">
            <span class="lang-badge-flag">{{ flagIcons[langCode] || '🌍' }}</span>
            <span>{{ langName }}</span>
          </div>
          <button class="switch-lang-inline btn btn-ghost btn-sm" @click="reselectLang">← 重新选择语言</button>
        </div>
        <LetterSwapTitle text="选择你的目标等级" tag="h1" />
        <p class="page-sub">选择适合你当前水平的考试等级，系统将为每个模块推荐对应难度的内容</p>
      </div>

      <div class="exam-badge-center">
        <span class="exam-name">{{ examName }}</span>
        <span class="exam-lang">{{ langName }} 等级体系</span>
      </div>

      <div class="pick-grid">
        <!-- 全部等级 -->
        <div class="pick-card all-level-card" @click="selectLevel(-1)">
          <span class="pick-icon all-pick-icon">ALL</span>
          <div class="pick-body">
            <h4 class="pick-title">全部等级</h4>
            <p class="pick-desc">不限制难度，浏览所有内容</p>
          </div>
          <span class="pick-arrow">→</span>
        </div>

        <div
          v-for="lv in examLevels"
          :key="lv.value"
          class="pick-card"
          @click="selectLevel(lv.value)"
        >
          <span class="pick-icon">{{ lv.examLabel }}</span>
          <div class="pick-body">
            <h4 class="pick-title">{{ lv.examName }}</h4>
            <p class="pick-desc">{{ lv.desc }}</p>
          </div>
          <span class="pick-arrow">→</span>
        </div>
      </div>
    </template>

    <!-- ========== 学习中心主页 ========== -->
    <template v-if="step === 'center'">
      <div class="page-header">
        <div class="header-top">
          <div class="header-badges">
            <div class="lang-badge">
              <span class="lang-badge-flag">{{ flagIcons[langCode] || '🌍' }}</span>
              <span>{{ langName }}</span>
              <span class="lang-badge-code">{{ langCode.toUpperCase() }}</span>
            </div>
            <div class="level-badge-inline">
              <span class="level-badge-label">{{ examName }}</span>
              <span class="level-badge-value">{{ currentLevelLabel }}</span>
            </div>
          </div>
          <div class="header-actions">
            <button class="switch-btn btn btn-secondary btn-sm" @click="reselectLang">切换语言</button>
            <button class="switch-btn btn btn-secondary btn-sm" @click="reselectLevel">切换等级</button>
          </div>
        </div>
        <LetterSwapTitle :text="langName + ' 学习中心'" tag="h1" />
        <p class="page-sub">{{ langDesc[langCode] || '系统学习，全面提升' }}</p>
      </div>

      <!-- 统计条 -->
      <div class="stats-strip">
                <div class="stat-item">
          <span class="stat-item-icon"></span>
          <span class="stat-item-val">{{ langStats.vocabCount }}</span>
          <span class="stat-item-lbl">词汇总量</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-item-icon"></span>
          <span class="stat-item-val">{{ langStats.studiedCount }}</span>
          <span class="stat-item-lbl">已学习</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-item-icon"></span>
          <span class="stat-item-val">{{ langStats.masteredCount }}</span>
          <span class="stat-item-lbl">已掌握</span>
        </div>
        <button class="refresh-btn btn btn-ghost btn-sm" @click="loadLangStats" :disabled="loadingStats" title="刷新数据">
          <span :class="{ spinning: loadingStats }"></span>
        </button>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loadingStats" class="skeleton-area">
        <Skeleton :rows="2" :columns="3" />
      </div>

      <!-- 学习模块网格 -->
      <div v-else class="center-grid">
        <router-link
          v-for="m in learnModules"
          :key="m.title"
          :to="m.to"
          class="center-card"
        >
          <div class="card-top">
            <span class="card-icon" :class="'icon-' + m.icon"></span>
            <span v-if="m.stat" class="card-stat-badge">{{ m.stat }}</span>
          </div>
          <h3 class="card-title">{{ m.title }}</h3>
          <p class="card-desc">{{ m.desc }}</p>
          <span class="card-link">进入 →</span>
        </router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 1000px; margin: 0 auto; padding-bottom: 60px; }
.page-header { text-align: center; padding: 24px 0 12px; }
.page-header :deep(.letter-swap-title) { font-size: 30px; font-weight: 800; color: var(--color-text); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--color-text-muted); margin-top: 4px; }

/* ===== 语言 / 等级选择（统一卡片网格） ===== */
.pick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  max-width: 920px;
  margin: 0 auto;
  padding: 8px 0 40px;
}
.pick-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px; border-radius: var(--radius-lg);
  background: rgba(255,255,255,0.78); backdrop-filter: blur(14px);
  border: 1.5px solid rgba(0,0,0,0.05); cursor: pointer;
  transition: all 0.3s var(--ease-smooth);
  text-align: left;
}
.pick-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 30px rgba(90,125,150,0.1);
  border-color: rgba(124,157,181,0.25);
}
.pick-icon {
  min-width: 50px; height: 50px; padding: 0 12px;
  border-radius: 14px;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff; font-size: 15px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0; white-space: nowrap; letter-spacing: 0.5px;
}
.lang-pick-icon { font-size: 22px; padding: 0 10px; }
.all-pick-icon {
  background: linear-gradient(135deg, #8e8b7e, #6e7a6b);
  font-size: 14px; letter-spacing: 1px;
}
.pick-body { flex: 1; min-width: 0; }
.pick-title { font-size: 15px; font-weight: 700; color: var(--color-text); margin: 0; }
.pick-native { font-weight: 400; font-size: 12.5px; color: var(--color-text-muted); margin-left: 6px; font-style: normal; }
.pick-desc { font-size: 12px; color: #888; margin: 3px 0 0; line-height: 1.5; }
.pick-arrow { font-size: 18px; color: #ccc; flex-shrink: 0; transition: color 0.2s; }
.pick-card:hover .pick-arrow { color: #5a7d96; }

/* ===== 等级选择页头 ===== */
.header-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.switch-lang-inline {
  padding: 8px 18px; border-radius: 8px;
  border: 1.5px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.55);
  color: var(--color-text-secondary); font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.25s;
}
.switch-lang-inline:hover { background: rgba(0,0,0,0.05); border-color: rgba(0,0,0,0.18); }

.exam-badge-center { display: flex; align-items: center; justify-content: center; gap: 10px; margin: 16px 0; }
.exam-name { font-size: 13px; padding: 5px 14px; border-radius: var(--radius-full); background: rgba(90,125,150,0.12); color: #5a7d96; font-weight: 700; }
.exam-lang { font-size: 14px; color: var(--color-text); font-weight: 600; }

/* ===== 学习中心 ===== */
.header-badges { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.lang-badge {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(124,157,181,0.12); color: #5a7d96;
  padding: 5px 14px; border-radius: var(--radius-full);
  font-size: 13px; font-weight: 600;
}
.lang-badge-flag { font-size: 16px; }
.lang-badge-code { font-size: 10px; opacity: 0.6; font-weight: 400; }

.level-badge-inline {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(39,174,96,0.1); color: #27ae60;
  padding: 5px 14px; border-radius: var(--radius-full);
  font-size: 13px; font-weight: 600;
}
.level-badge-label { font-size: 10px; opacity: 0.7; }
.level-badge-value { font-size: 13px; }

.header-actions { display: flex; gap: 8px; }
.switch-btn {
  padding: 8px 18px; border-radius: 8px;
  border: 1.5px solid rgba(0,0,0,0.1); background: rgba(255,255,255,0.55);
  color: var(--color-text-secondary); font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.25s;
}
.switch-btn:hover { background: rgba(0,0,0,0.05); border-color: rgba(0,0,0,0.18); }

/* 统计条 */
.stats-strip {
  display: flex; align-items: center; justify-content: center; gap: 0;
  background: rgba(255,255,255,0.72); backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg);
  padding: 18px 12px; margin: 16px 0 20px;
}
.stat-item { display: flex; align-items: center; gap: 6px; padding: 0 24px; }
.stat-item-icon { font-size: 20px; }
.stat-item-val { font-size: 22px; font-weight: 800; color: var(--color-text); }
.stat-item-lbl { font-size: 12px; color: var(--color-text-muted); }
.stat-divider { width: 1px; height: 32px; background: rgba(0,0,0,0.08); }

.refresh-btn { margin-left: auto; padding: 6px 10px; border-radius: 10px; border: 1.5px solid rgba(0,0,0,0.08); background: rgba(255,255,255,0.6); cursor: pointer; font-size: 14px; transition: all 0.25s; }
.refresh-btn:hover { background: rgba(90,125,150,0.08); border-color: rgba(90,125,150,0.2); }
.refresh-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.refresh-btn .spinning { display: inline-block; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.skeleton-area { padding: 8px 16px; }

/* 模块网格 */
.center-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
  padding: 4px 0 30px;
}
.center-card {
  background: rgba(255,255,255,0.72); backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg);
  padding: 24px; text-decoration: none;
  transition: all 0.35s var(--ease-smooth);
  display: flex; flex-direction: column; gap: 6px;
  position: relative;
}
.center-card:hover { transform: translateY(-4px); box-shadow: 0 12px 36px rgba(90,125,150,0.12); border-color: rgba(124,157,181,0.2); }
.card-top { display: flex; align-items: center; justify-content: space-between; }
.card-stat-badge {
  font-size: 11px; color: #5a7d96; background: rgba(124,157,181,0.1);
  padding: 3px 10px; border-radius: var(--radius-full); font-weight: 600;
}
.card-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin: 4px 0 0; }
.card-desc { font-size: 13px; color: var(--color-text-muted); margin: 0; line-height: 1.5; }
.card-link { font-size: 12px; color: #7c9db5; font-weight: 600; margin-top: auto; padding-top: 6px; }

.card-icon { width: 42px; height: 42px; border-radius: 12px; display: flex; align-items: center; justify-content: center; }
.card-icon.icon-vocab { background: #5a7d961a; } .card-icon.icon-vocab::after { content: ""; display: block; width: 22px; height: 22px; background: #5a7d96; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNNCAxOS41QTIuNSAyLjUgMCAwMTYuNSAxN0gyMCIvPjxwYXRoIGQ9Ik02LjUgMkgyMHYyMEg2LjVBMi41IDIuNSAwIDAxNCAxOS41di0xNUEyLjUgMi41IDAgMDE2LjUgMnoiLz48bGluZSB4MT0iOCIgeTE9IjciIHgyPSIxNiIgeTI9IjciLz48bGluZSB4MT0iOCIgeTE9IjExIiB4Mj0iMTQiIHkyPSIxMSIvPjwvc3ZnPg==) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNNCAxOS41QTIuNSAyLjUgMCAwMTYuNSAxN0gyMCIvPjxwYXRoIGQ9Ik02LjUgMkgyMHYyMEg2LjVBMi41IDIuNSAwIDAxNCAxOS41di0xNUEyLjUgMi41IDAgMDE2LjUgMnoiLz48bGluZSB4MT0iOCIgeTE9IjciIHgyPSIxNiIgeTI9IjciLz48bGluZSB4MT0iOCIgeTE9IjExIiB4Mj0iMTQiIHkyPSIxMSIvPjwvc3ZnPg==) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-practice { background: #27ae601a; } .card-icon.icon-practice::after { content: ""; display: block; width: 22px; height: 22px; background: #27ae60; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTQgMkg2YTIgMiAwIDAwLTIgMnYxNmEyIDIgMCAwMDIgMmgxMmEyIDIgMCAwMDItMlY4eiIvPjxwb2x5bGluZSBwb2ludHM9IjE0LDIsMTQsOCwyMCw4Ii8+PGxpbmUgeDE9IjE2IiB5MT0iMTMiIHgyPSI4IiB5Mj0iMTMiLz48L3N2Zz4=) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTQgMkg2YTIgMiAwIDAwLTIgMnYxNmEyIDIgMCAwMDIgMmgxMmEyIDIgMCAwMDItMlY4eiIvPjxwb2x5bGluZSBwb2ludHM9IjE0LDIsMTQsOCwyMCw4Ii8+PGxpbmUgeDE9IjE2IiB5MT0iMTMiIHgyPSI4IiB5Mj0iMTMiLz48L3N2Zz4=) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-grammar { background: #9b59b61a; } .card-icon.icon-grammar::after { content: ""; display: block; width: 22px; height: 22px; background: #9b59b6; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSIxMCIvPjxsaW5lIHgxPSIyIiB5MT0iMTIiIHgyPSIyMiIgeTI9IjEyIi8+PHBhdGggZD0iTTEyIDJhMTUuMyAxNS4zIDAgMDE0IDEwIDE1LjMgMTUuMyAwIDAxLTQgMTAiLz48L3N2Zz4=) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSIxMCIvPjxsaW5lIHgxPSIyIiB5MT0iMTIiIHgyPSIyMiIgeTI9IjEyIi8+PHBhdGggZD0iTTEyIDJhMTUuMyAxNS4zIDAgMDE0IDEwIDE1LjMgMTUuMyAwIDAxLTQgMTAiLz48L3N2Zz4=) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-reading { background: #e67e221a; } .card-icon.icon-reading::after { content: ""; display: block; width: 22px; height: 22px; background: #e67e22; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNNCAxOS41QTIuNSAyLjUgMCAwMTYuNSAxN0gyMCIvPjxwYXRoIGQ9Ik02LjUgMkgyMHYyMEg2LjVBMi41IDIuNSAwIDAxNCAxOS41di0xNUEyLjUgMi41IDAgMDE2LjUgMnoiLz48bGluZSB4MT0iOCIgeTE9IjciIHgyPSIxNiIgeTI9IjciLz48L3N2Zz4=) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNNCAxOS41QTIuNSAyLjUgMCAwMTYuNSAxN0gyMCIvPjxwYXRoIGQ9Ik02LjUgMkgyMHYyMEg2LjVBMi41IDIuNSAwIDAxNCAxOS41di0xNUEyLjUgMi41IDAgMDE2LjUgMnoiLz48bGluZSB4MT0iOCIgeTE9IjciIHgyPSIxNiIgeTI9IjciLz48L3N2Zz4=) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-writing { background: #3498db1a; } .card-icon.icon-writing::after { content: ""; display: block; width: 22px; height: 22px; background: #3498db; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTIgMjBoOSIvPjxwYXRoIGQ9Ik0xNi41IDMuNWEyLjEyMSAyLjEyMSAwIDAxMyAzTDcgMTlsLTQgMSAxLTRMMTYuNSAzLjV6Ii8+PC9zdmc+) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTIgMjBoOSIvPjxwYXRoIGQ9Ik0xNi41IDMuNWEyLjEyMSAyLjEyMSAwIDAxMyAzTDcgMTlsLTQgMSAxLTRMMTYuNSAzLjV6Ii8+PC9zdmc+) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-ai { background: #27ae601a; } .card-icon.icon-ai::after { content: ""; display: block; width: 22px; height: 22px; background: #27ae60; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cmVjdCB4PSIzIiB5PSIxMSIgd2lkdGg9IjE4IiBoZWlnaHQ9IjExIiByeD0iMiIvPjxwYXRoIGQ9Ik03IDExVjdhNSA1IDAgMDExMCAwdjQiLz48Y2lyY2xlIGN4PSIxMiIgY3k9IjE2IiByPSIxIi8+PC9zdmc+) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cmVjdCB4PSIzIiB5PSIxMSIgd2lkdGg9IjE4IiBoZWlnaHQ9IjExIiByeD0iMiIvPjxwYXRoIGQ9Ik03IDExVjdhNSA1IDAgMDExMCAwdjQiLz48Y2lyY2xlIGN4PSIxMiIgY3k9IjE2IiByPSIxIi8+PC9zdmc+) no-repeat center; -webkit-mask-size: contain; }
.card-icon.icon-link { background: #9b59b61a; } .card-icon.icon-link::after { content: ""; display: block; width: 22px; height: 22px; background: #9b59b6; mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTAgMTNhNSA1IDAgMDA3LjU0LjU0bDMtM2E1IDUgMCAwMC03LjA3LTcuMDdsLTEuNzIgMS43MSIvPjxwYXRoIGQ9Ik0xNCAxMWE1IDUgMCAwMC03LjU0LS41NGwtMyAzYTUgNSAwIDAwNy4wNyA3LjA3bDEuNzEtMS43MSIvPjwvc3ZnPg==) no-repeat center; mask-size: contain; -webkit-mask: url(data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSIyIj48cGF0aCBkPSJNMTAgMTNhNSA1IDAgMDA3LjU0LjU0bDMtM2E1IDUgMCAwMC03LjA3LTcuMDdsLTEuNzIgMS43MSIvPjxwYXRoIGQ9Ik0xNCAxMWE1IDUgMCAwMC03LjU0LS41NGwtMyAzYTUgNSAwIDAwNy4wNyA3LjA3bDEuNzEtMS43MSIvPjwvc3ZnPg==) no-repeat center; -webkit-mask-size: contain; }

@media (max-width: 768px) {
  .stats-strip { flex-wrap: wrap; gap: 8px; }
  .stat-divider { display: none; }
  .center-grid { grid-template-columns: 1fr; }
  .courses-mini { flex-direction: column; align-items: center; }
  .level-grid { grid-template-columns: 1fr; }
  .header-top { flex-wrap: wrap; gap: 8px; }
  .header-actions { width: 100%; justify-content: flex-end; }
}
</style>

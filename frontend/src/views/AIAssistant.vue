<script setup>
/**
 * AIAssistant.vue — AI 学习助手
 *
 * 整合全局语言+等级，提供:
 *   智能问答 — 语言学习问题解答
 *   例句生成 — 输入单词/汉语，AI 生成对应等级例句
 *   语法纠错 — AI 逐词纠错
 */
import { ref, onMounted, watch, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useLanguageStore } from '@/stores/language'
import { getExamLevels } from '@/data/examLevels'
import { toast } from '@/composables/useToast'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'
import LetterSwapTitle from '@/components/effects/LetterSwapTitle.vue'
import { langName } from '@/config/languages'

const authStore = useAuthStore()
const languageStore = useLanguageStore()

const activeTab = ref('qa')
const loading = ref(false)

// ====== 全局语言 + 等级 ======
const currentLang = computed(() => authStore.targetLanguage || authStore.user?.currentLangCode || 'en')
const examLevels = computed(() => getExamLevels(currentLang.value))

const currentLevelLabel = computed(() => {
  const lv = examLevels.value.find(l => l.value === authStore.targetLevel)
  return lv ? `${lv.examLabel} (${lv.examName})` : '全部等级'
})

// ====== Tab 1: 智能问答 ======
const qaQuestion = ref('')
const qaAnswer = ref('')
const qaLoading = ref(false)
const qaHistory = ref([]) // 当前会话的对话历史 [{role: 'user', content}, {role: 'assistant', content}]

async function askQuestion() {
  if (!qaQuestion.value.trim()) { toast.warning('请输入问题'); return }
  const q = qaQuestion.value
  qaQuestion.value = ''
  qaLoading.value = true
  qaAnswer.value = ''

  // 构建历史上下文（最近 10 轮）
  const recentHistory = qaHistory.value.slice(-20)

  try {
    const res = await fetch(`${API_BASE_URL}/ai/ask/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: q,
        langCode: currentLang.value,
        history: recentHistory,
      }),
    })

    if (!res.ok) {
      qaAnswer.value = 'AI 服务暂不可用'
      qaLoading.value = false
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const token = trimmed.slice(5)
          if (token) qaAnswer.value += token
        }
      }
    }

    if (buffer.trim()) {
      const t = buffer.trim()
      if (t.startsWith('data:')) {
        const token = t.slice(5)
        if (token) qaAnswer.value += token
      }
    }
    // 保存到当前会话历史
    qaHistory.value.push({ role: 'user', content: q })
    qaHistory.value.push({ role: 'assistant', content: qaAnswer.value })
  } catch (e) {
    if (!qaAnswer.value) qaAnswer.value = 'AI 服务暂不可用'
    toast.error('AI 服务暂不可用')
  } finally { qaLoading.value = false }
}

// ====== Tab 2: 例句生成 ======
const exampleWord = ref('')
const examples = ref([])
const exampleLoading = ref(false)

async function generateExamples(append = false) {
  const word = exampleWord.value.trim()
  if (!word) { toast.warning('请输入一个单词或汉语'); return }
  if (!append) examples.value = []
  exampleLoading.value = true
  try {
    const data = await fetchJson(`${API_BASE_URL}/ai/examples`, {
      method: 'POST',
      body: {
        word, langCode: currentLang.value,
        count: 3,
        level: currentLevelLabel.value,
      },
    })
    const newOnes = data.data?.sentences || []
    if (newOnes.length === 0) { toast.info('未生成例句，请稍后再试'); return }
    examples.value = [...examples.value, ...newOnes]
  } catch (e) {
    toast.error('AI 服务暂不可用')
  } finally { exampleLoading.value = false }
}

// ====== Tab 3: 语法纠错 ======
const checkText = ref('')
const checkResult = ref(null)
const checkLoading = ref(false)

async function checkGrammar() {
  checkResult.value = null
  if (!checkText.value.trim()) { toast.warning('请输入要检查的句子'); return }
  checkLoading.value = true
  try {
    const data = await fetchJson(`${API_BASE_URL}/ai/grammar-check`, {
      method: 'POST',
      body: { text: checkText.value, langCode: currentLang.value },
    })
    checkResult.value = data.data
    if (data.data && !data.data.hasErrors) toast.success('没有发现语法错误！')
  } catch (e) {
    toast.error('AI 服务暂不可用')
  } finally { checkLoading.value = false }
}

onMounted(async () => {
  await languageStore.fetchLanguages()
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <LetterSwapTitle text="AI 学习助手" tag="h1" />
      <p class="page-sub">
        <span class="lang-badge">{{ langName(currentLang) }}</span>
        <span class="level-badge">{{ currentLevelLabel }}</span>
        · 根据你当前等级智能辅助
      </p>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button :class="{ active: activeTab === 'qa' }" @click="activeTab = 'qa'">
        <span class="tab-icon"></span>智能问答
      </button>
      <button :class="{ active: activeTab === 'examples' }" @click="activeTab = 'examples'">
        <span class="tab-icon"></span>例句生成
      </button>
      <button :class="{ active: activeTab === 'grammar' }" @click="activeTab = 'grammar'">
        <span class="tab-icon"></span>语法纠错
      </button>
    </div>

    <div class="content-card">
      <!-- ====== 智能问答 ====== -->
      <div v-if="activeTab === 'qa'">
        <h3 class="section-title">AI 智能问答</h3>
        <p class="section-hint">{{ langName(currentLang) }} · {{ currentLevelLabel }} — 用中文或外语提问</p>
        <textarea v-model="qaQuestion" class="input-area"
          placeholder="例如：'go 和 goes 有什么区别？' 或 '帮我解释一下法语的条件式'..."
          rows="3" @keyup.ctrl.enter="askQuestion" />
        <button class="btn primary" :disabled="qaLoading || !qaQuestion.trim()" @click="askQuestion">
          {{ qaLoading ? ' AI 思考中...' : ' 提问' }}
        </button>
        <div v-if="qaAnswer" class="answer-block">
          <h4>AI 回答</h4>
          <div class="answer-text">{{ qaAnswer }}</div>
        </div>
      </div>

      <!-- ====== 例句生成 ====== -->
      <div v-if="activeTab === 'examples'">
        <h3 class="section-title">{{ langName(currentLang) }} 例句生成</h3>
        <p class="section-hint">{{ currentLevelLabel }} — 输入单词或汉语，AI 生成符合你等级的例句</p>
        <input v-model="exampleWord" class="input-field"
          placeholder="例如: beautiful / 美丽 / しかし / 旅行..."
          @keyup.enter="generateExamples(false)" />
        <button class="btn primary" :disabled="exampleLoading || !exampleWord.trim()" @click="generateExamples(false)">
          {{ exampleLoading ? ' AI 思考中...' : ' 生成例句' }}
        </button>
        <div v-if="examples.length" class="example-list">
          <div v-for="(s, i) in examples" :key="i" class="example-card">
            <p class="ex-sentence">{{ s.sentence }}</p>
            <p class="ex-translation">{{ s.translation }}</p>
          </div>
          <button class="btn secondary" :disabled="exampleLoading" @click="generateExamples(true)">
            {{ exampleLoading ? ' ...' : ' 继续生成' }}
          </button>
        </div>
      </div>

      <!-- ====== 语法纠错 ====== -->
      <div v-if="activeTab === 'grammar'">
        <h3 class="section-title">AI 语法纠错</h3>
        <p class="section-hint">{{ langName(currentLang) }} — 逐词检查，给出规则说明</p>
        <textarea v-model="checkText" class="input-area"
          placeholder="请输入想要检查的句子..." rows="3" />
        <button class="btn primary" :disabled="checkLoading || !checkText.trim()" @click="checkGrammar">
          {{ checkLoading ? ' 检查中...' : ' 检查语法' }}
        </button>
        <div v-if="checkResult" class="check-result">
          <div v-if="checkResult.hasErrors" class="result-block-error">
            <p><strong>修正后:</strong> {{ checkResult.correctedText }}</p>
            <div v-if="checkResult.errors?.length" class="error-list">
              <h4>错误详情</h4>
              <div v-for="(e, i) in checkResult.errors" :key="i" class="error-card">
                <span class="e-orig">{{ e.original }}</span>
                <span class="e-arrow">→</span>
                <span class="e-correct">{{ e.correction }}</span>
                <span class="e-rule">{{ e.rule }}</span>
              </div>
            </div>
          </div>
          <p v-else class="no-error"> 没有发现语法错误！</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-wrap { max-width: 720px; margin: 0 auto; padding-bottom: 40px; }
.page-header { text-align: center; padding: 24px 0 12px; }
.page-header :deep(.letter-swap-title) { font-size: 28px; font-weight: 800; color: var(--color-text); }
.page-sub { font-size: 14px; color: var(--color-text-muted); display: flex; align-items: center; gap: 8px; justify-content: center; }
.lang-badge {
  padding: 2px 10px; border-radius: 8px; background: rgba(77,150,255,0.1); color: #4d96ff;
  font-size: 12px; font-weight: 700;
}
.level-badge {
  padding: 2px 10px; border-radius: 8px; background: rgba(107,203,119,0.1); color: #3fa65a;
  font-size: 12px; font-weight: 700;
}

/* Tab */
.tab-bar { display: flex; gap: 8px; justify-content: center; margin: 16px 0; }
.tab-bar button {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; border-radius: 22px; border: 1.5px solid #ddd8e9;
  background: rgba(255,255,255,0.6); font-size: 14px; font-weight: 600;
  cursor: pointer; transition: all 0.25s; color: #5f5a70;
}
.tab-bar button:hover, .tab-bar button.active {
  border-color: #4d96ff; color: #4d96ff; background: rgba(77,150,255,0.06);
}
.tab-icon { font-size: 16px; }

/* 内容区 */
.content-card {
  background: rgba(255,255,255,0.8); backdrop-filter: blur(14px);
  border: 1px solid rgba(0,0,0,0.05); border-radius: var(--radius-lg);
  padding: 28px;
}
.section-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin-bottom: 4px; }
.section-hint { font-size: 13px; color: #8f88a8; margin-bottom: 16px; }

/* 按钮 */
.btn {
  padding: 11px 28px; border-radius: 10px; border: none;
  font-size: 15px; font-weight: 600; cursor: pointer; margin: 8px 0 16px;
  transition: all 0.25s;
}
.btn.primary { background: linear-gradient(135deg, #7bb7ff, #4d96ff); color: #fff; }
.btn.primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 16px rgba(77,150,255,0.3); }
.btn.secondary {
  background: #fff; border: 1.5px solid #7bb7ff; color: #4d96ff; margin-top: 4px;
}
.btn.secondary:hover:not(:disabled) { background: rgba(123,183,255,0.06); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* 输入框 */
.input-area {
  width: 100%; padding: 14px; border-radius: 12px; border: 1.5px solid #e6e0f2;
  background: #fafafa; color: var(--color-text); font-size: 15px; line-height: 1.7;
  resize: vertical; outline: none; font-family: var(--font-body); margin-bottom: 4px;
}
.input-area:focus { border-color: #7bb7ff; box-shadow: 0 0 0 3px rgba(123,183,255,0.1); }
.input-field {
  width: 100%; padding: 12px 16px; border-radius: 12px; border: 1.5px solid #e6e0f2;
  background: #fafafa; font-size: 15px; outline: none; margin-bottom: 4px;
  color: var(--color-text);
}
.input-field:focus { border-color: #7bb7ff; box-shadow: 0 0 0 3px rgba(123,183,255,0.1); }

/* 答案 */
.answer-block { margin-top: 16px; padding: 18px; background: #f6f9ff; border-radius: 12px; border: 1px solid #edeaf5; }
.answer-block h4 { font-size: 14px; font-weight: 700; color: var(--color-text); margin-bottom: 8px; }
.answer-text { font-size: 15px; color: #3f3a4d; line-height: 1.8; white-space: pre-wrap; }

/* 例句列表 */
.example-list { margin-top: 12px; }
.example-card { padding: 14px 18px; background: #f6f9ff; border-radius: 10px; border: 1px solid #edeaf5; margin-bottom: 8px; }
.ex-sentence { font-size: 16px; font-weight: 600; color: var(--color-text); }
.ex-translation { font-size: 14px; color: #6b647e; margin-top: 4px; }

/* 语法纠错 */
.check-result { margin-top: 16px; }
.result-block-error { padding: 16px; background: #fff5f5; border-radius: 10px; border: 1px solid #ffd9d9; }
.result-block-error h4 { font-size: 14px; font-weight: 700; color: var(--color-text); margin: 12px 0 8px; }
.no-error { color: #3fa65a; font-weight: 600; font-size: 15px; }
.error-list { margin-top: 8px; }
.error-card { padding: 8px 12px; background: #fff; border-radius: 8px; margin: 6px 0; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.e-orig { color: #ff6b6b; text-decoration: line-through; }
.e-correct { color: #3fa65a; font-weight: 600; }
.e-rule { color: #6b647e; font-size: 12px; margin-left: auto; }
.e-arrow { color: #b9b3d0; }
</style>

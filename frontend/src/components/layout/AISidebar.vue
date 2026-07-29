<script setup>
/**
 * AISidebar.vue — 全局 AI 助手侧边栏
 *
 * 右下角浮动按钮 → 点击弹出右侧面板
 * 所有模块页面均可随时调用
 * 功能: 智能问答 / 例句生成 / 语法纠错
 */
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getExamLevels } from '@/data/examLevels'
import PlainIcon from '@/components/common/PlainIcon.vue'
import { toast } from '@/composables/useToast'
import { API_BASE_URL } from '@/config'
import { langName } from '@/config/languages'

const authStore = useAuthStore()
const open = ref(false)
const activeTab = ref('qa')
const loading = ref(false)
const history = ref([]) // 对话历史

// ====== 全局语言 + 等级 ======
const currentLang = computed(() => authStore.targetLanguage || authStore.user?.currentLangCode || 'en')
const examLevels = computed(() => getExamLevels(currentLang.value))
const currentLevelLabel = computed(() => {
  const lv = examLevels.value.find(l => l.value === authStore.targetLevel)
  return lv ? `${lv.examLabel} (${lv.examName})` : '全部等级'
})

// ====== Tab 1: 智能问答 ======
const qaQuestion = ref('')
const qaLoading = ref(false)

async function askQuestion() {
  if (!qaQuestion.value.trim()) return
  const q = qaQuestion.value
  qaQuestion.value = ''
  history.value.push({ role: 'user', content: q })
  qaLoading.value = true

  // 保存用户消息到后端
  const userId = authStore.user?.id
  if (userId) {
    fetch(`${API_BASE_URL}/history/chat`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, langCode: currentLang.value, role: 'user', content: q }),
    }).catch(() => {})
  }

  // 先插入空的 AI 占位，后续逐 token 追加
  const aiMsgIdx = history.value.length
  history.value.push({ role: 'ai', content: '' })

  // 取最近 10 轮对话（20 条消息）作为上下文传给后端
  // 排除刚插入的当前用户消息和 AI 占位
  const contextMsgs = history.value.slice(0, history.value.length - 2) // 去掉 user(q) 和 ai('')
  const recentHistory = contextMsgs.slice(-20).map(m => ({
    role: m.role === 'ai' ? 'assistant' : 'user',
    content: m.content,
  }))

  try {
    const res = await fetch(`${API_BASE_URL}/ai/ask/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: q, lang: currentLang.value, history: recentHistory }),
    })

    if (!res.ok) {
      history.value[aiMsgIdx].content = 'AI 暂不可用'
      qaLoading.value = false
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let fullAnswer = ''

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
          if (token) {
            fullAnswer += token
            history.value[aiMsgIdx].content = fullAnswer
          }
        }
      }
    }

    if (buffer.trim()) {
      const t = buffer.trim()
      if (t.startsWith('data:')) {
        const token = t.slice(5)
        if (token) {
          fullAnswer += token
          history.value[aiMsgIdx].content = fullAnswer
        }
      }
    }

    // 保存完整的 AI 回复到后端
    if (userId && fullAnswer) {
      fetch(`${API_BASE_URL}/history/chat`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, langCode: currentLang.value, role: 'ai', content: fullAnswer }),
      }).catch(() => {})
    }
  } catch (e) {
    if (!history.value[aiMsgIdx].content) {
      history.value[aiMsgIdx].content = 'AI 服务暂不可用，请稍后重试'
    }
  } finally { qaLoading.value = false }
}

// ====== Tab 2: 例句生成 ======
const exampleWord = ref('')
const examples = ref([])

async function generateExamples(append = false) {
  const word = exampleWord.value.trim()
  if (!word) { toast.warning('请输入一个单词或汉语'); return }
  if (!append) examples.value = []
  loading.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/ai/examples`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ word, lang: currentLang.value, count: 3, level: currentLevelLabel.value }),
    })
    const data = await res.json()
    const newOnes = data.data?.sentences || []
    if (newOnes.length === 0) { toast.info('未生成例句'); return }
    examples.value = [...examples.value, ...newOnes]
  } catch (e) { toast.error('AI 暂不可用') }
  finally { loading.value = false }
}

// ====== Tab 3: 语法纠错 ======
const checkText = ref('')
const checkResult = ref(null)

async function checkGrammar() {
  if (!checkText.value.trim()) return
  checkResult.value = null
  loading.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/ai/grammar-check`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: checkText.value, lang: currentLang.value }),
    })
    const data = await res.json()
    checkResult.value = data.data
  } catch (e) { toast.error('AI 暂不可用') }
  finally { loading.value = false }
}

// 加载历史对话
const chatHistory = ref([])
const historyLoading = ref(false)

async function loadChatHistory() {
  if (activeTab.value !== 'qa' && activeTab.value !== 'history') return
  const userId = authStore.user?.id
  if (!userId) return
  historyLoading.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/history/chat?userId=${userId}&limit=50`)
    const json = await res.json()
    chatHistory.value = json.data || []
  } catch (e) { chatHistory.value = [] }
  finally { historyLoading.value = false }
}

// 切换 tab 时清理旧状态
watch(activeTab, (tab) => {
  checkResult.value = null; examples.value = []
  if (tab === 'history') loadChatHistory()
})

function toggle() { open.value = !open.value }

// 监听语言切换，清理历史
// 语言切换时不再清空历史，由用户手动管理
</script>

<template>
  <!-- 浮动按钮 -->
  <button class="ai-fab" :class="{ open }" @click="toggle" title="AI 助手">
    <span v-if="!open" class="fab-icon">
      <PlainIcon name="sparkle" />
    </span>
    <span v-else class="fab-close">✕</span>
  </button>

  <!-- 遮罩 -->
  <Transition name="fade">
    <div v-if="open" class="ai-overlay" @click="toggle" />
  </Transition>

  <!-- 侧边栏 -->
  <Transition name="slide">
    <div v-if="open" class="ai-sidebar">
      <div class="sidebar-header">
        <div class="header-top">
          <h3>AI 学习助手</h3>
          <span class="lang-tag">{{ langName(currentLang) }} · {{ currentLevelLabel }}</span>
        </div>
        <button class="close-btn" @click="toggle">✕</button>
      </div>

      <!-- Tab -->
      <div class="sidebar-tabs">
        <button :class="{ active: activeTab === 'qa' }" @click="activeTab = 'qa'; loadChatHistory()">
          <PlainIcon name="chat" /> 问答
        </button>
        <button :class="{ active: activeTab === 'examples' }" @click="activeTab = 'examples'">
          <PlainIcon name="book" /> 例句
        </button>
        <button :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'; loadChatHistory()">
          <span class="plain-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </span> 历史
        </button>
        <button :class="{ active: activeTab === 'grammar' }" @click="activeTab = 'grammar'">
          <PlainIcon name="search" /> 纠错
        </button>
      </div>

      <div class="sidebar-body">
        <!-- ====== 问答模式 ====== -->
        <div v-if="activeTab === 'qa'" class="qa-body">
          <div class="chat-list">
            <div v-if="history.length === 0" class="empty-chat">
              <p> 你好！我是你的 {{ langName(currentLang) }} 学习助手</p>
              <p class="hint">用中文或外语向我提问吧</p>
            </div>
            <div v-for="(msg, i) in history" :key="i" class="chat-msg" :class="msg.role">
              <div class="msg-content">{{ msg.content }}</div>
            </div>
            <div v-if="qaLoading" class="chat-msg ai typing">AI 思考中...</div>
          </div>
          <div class="chat-actions" v-if="history.length > 0"><button class="chat-action-btn" @click="history = []">清空记录</button></div>
          <div class="chat-input-row">
            <input v-model="qaQuestion"
              @keyup.enter="askQuestion"
              class="chat-input" />
            <button class="send-btn" :disabled="!qaQuestion.trim() || qaLoading" @click="askQuestion">→</button>
          </div>
        </div>

        <!-- ====== 例句模式 ====== -->
        <div v-if="activeTab === 'examples'">
          <div class="input-group">
            <input v-model="exampleWord" class="full-input"
              placeholder="输入单词或汉语..."
              @keyup.enter="generateExamples(false)" />
            <button class="ai-btn primary" :disabled="loading || !exampleWord.trim()" @click="generateExamples(false)">
              {{ loading ? '生成中...' : ' 生成例句' }}
            </button>
          </div>
          <div v-if="examples.length" class="example-list">
            <div v-for="(s, i) in examples" :key="i" class="ex-card">
              <p class="ex-s">{{ s.sentence }}</p>
              <p class="ex-t">{{ s.translation }}</p>
            </div>
            <button class="ai-btn secondary" :disabled="loading" @click="generateExamples(true)"> 继续生成</button>
          </div>
        </div>

        <!-- ====== 历史模式 ====== -->
        <div v-if="activeTab === 'history'">
          <div class="sidebar-panel-title">历史对话</div>
          <div v-if="historyLoading" class="loading-text">加载中...</div>
          <div v-else-if="chatHistory.length === 0" class="empty-chat">
            <p>暂无历史对话记录</p>
          </div>
          <div v-else class="chat-history-list">
            <div v-for="(msg, i) in chatHistory" :key="msg.id || i" class="chat-msg" :class="msg.role">
              <div class="msg-content">{{ msg.content }}</div>
              <div class="msg-time">{{ (msg.created_at || '').substring(0, 16) }}</div>
            </div>
          </div>
        </div>

        <!-- ====== 纠错模式 ====== -->
        <div v-if="activeTab === 'grammar'">
          <div class="input-group">
            <textarea v-model="checkText" class="full-textarea"
              placeholder="粘贴你要检查的句子..."
              rows="3" />
            <button class="ai-btn primary" :disabled="loading || !checkText.trim()" @click="checkGrammar">
              {{ loading ? '检查中...' : ' 检查语法' }}
            </button>
          </div>
          <div v-if="checkResult" class="check-result">
            <div v-if="checkResult.hasErrors" class="cb">
              <p><strong>修正后:</strong> {{ checkResult.correctedText }}</p>
              <div v-if="checkResult.errors?.length" class="errs">
                <div v-for="(e, i) in checkResult.errors" :key="i" class="err">
                  <span class="eo">{{ e.original }}</span> → <span class="ec">{{ e.correction }}</span>
                  <span class="er">{{ e.rule }}</span>
                </div>
              </div>
            </div>
            <p v-else class="no-err"> 没有语法错误</p>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* ====== 浮动按钮 ====== */
.ai-fab {
  position: fixed; bottom: 28px; right: 28px; z-index: 9000;
  width: 52px; height: 52px; border-radius: 50%; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96);
  color: #fff; font-size: 20px; cursor: pointer;
  box-shadow: 0 4px 20px rgba(90,125,150,0.35);
  transition: all 0.3s ease;
  display: flex; align-items: center; justify-content: center;
}
.ai-fab:hover { transform: scale(1.08); box-shadow: 0 6px 28px rgba(90,125,150,0.45); }
.ai-fab.open { background: #888; }

/* ====== 遮罩 ====== */
.ai-overlay {
  position: fixed; inset: 0; z-index: 8998;
  background: rgba(0,0,0,0.15); backdrop-filter: blur(2px);
}

/* ====== 侧边栏 ====== */
.ai-sidebar {
  position: fixed; top: 0; right: 0; bottom: 0; z-index: 8999;
  width: 420px; max-width: 100vw;
  background: rgba(255,255,255,0.96); backdrop-filter: blur(20px);
  box-shadow: -4px 0 40px rgba(0,0,0,0.08);
  display: flex; flex-direction: column;
}
.sidebar-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 20px 22px 12px; border-bottom: 1px solid rgba(0,0,0,0.05);
}
.header-top h3 { font-size: 18px; font-weight: 800; color: var(--color-text); margin: 0; }
.lang-tag {
  display: inline-block; margin-top: 4px; padding: 2px 8px; border-radius: 6px;
  background: rgba(90,125,150,0.08); color: #5a7d96; font-size: 11px; font-weight: 600;
}
.close-btn {
  width: 32px; height: 32px; border-radius: 50%; border: none;
  background: rgba(0,0,0,0.05); color: #666; font-size: 16px; cursor: pointer;
  transition: all 0.2s;
}
.close-btn:hover { background: rgba(0,0,0,0.1); }

/* ====== Tab ====== */
.sidebar-tabs { display: flex; gap: 4px; padding: 10px 16px; border-bottom: 1px solid rgba(0,0,0,0.04); }
.sidebar-tabs button {
  flex: 1; padding: 8px 0; border-radius: 10px; border: 1.5px solid transparent;
  background: transparent; font-size: 13px; font-weight: 600; color: #888; cursor: pointer;
  transition: all 0.2s;
}
.sidebar-tabs button.active { border-color: #5a7d96; color: #5a7d96; background: rgba(90,125,150,0.04); }

/* ====== 主体 ====== */
.sidebar-body { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; }

/* 问答模式 */
.qa-body { flex: 1; display: flex; flex-direction: column; }
.chat-list { flex: 1; overflow-y: auto; margin-bottom: 12px; }
.empty-chat { text-align: center; padding: 40px 20px; color: #aaa; }
.empty-chat p { margin: 0; font-size: 14px; }
.empty-chat .hint { font-size: 12px; margin-top: 6px; color: #ccc; }
.chat-msg { margin-bottom: 12px; display: flex; }
.chat-msg.user { justify-content: flex-end; }
.chat-msg.user .msg-content {
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  border-radius: 14px 14px 4px 14px;
}
.chat-msg.ai .msg-content {
  background: #f0f2f5; color: #444;
  border-radius: 14px 14px 14px 4px;
}
.chat-msg.typing .msg-content { background: #f0f2f5; color: #aaa; font-style: italic; }
.msg-content { padding: 10px 14px; max-width: 85%; font-size: 14px; line-height: 1.6; white-space: pre-wrap; }

.chat-input-row { display: flex; gap: 8px; align-items: center; padding-top: 8px; }
.chat-input {
  flex: 1; padding: 10px 14px; border-radius: 22px; border: 1.5px solid #e0e0e0;
  background: #f8f9fa; font-size: 14px; outline: none; color: var(--color-text);
}
.chat-input:focus { border-color: #7c9db5; }
.send-btn {
  width: 38px; height: 38px; border-radius: 50%; border: none;
  background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff;
  font-size: 18px; cursor: pointer; flex-shrink: 0; transition: all 0.2s;
}
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.send-btn:hover:not(:disabled) { transform: scale(1.05); }

/* 例句 & 纠错通用 */
.input-group { margin-bottom: 14px; }
.full-input, .full-textarea {
  width: 100%; padding: 10px 14px; border-radius: 12px; border: 1.5px solid #e0e0e0;
  background: #f8f9fa; font-size: 14px; outline: none; color: var(--color-text);
  font-family: var(--font-body); resize: vertical; margin-bottom: 8px;
}
.full-input:focus, .full-textarea:focus { border-color: #7c9db5; }
.ai-btn {
  width: 100%; padding: 10px 0; border-radius: 10px; border: none;
  font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s;
}
.ai-btn.primary { background: linear-gradient(135deg, #7c9db5, #5a7d96); color: #fff; }
.ai-btn.primary:hover:not(:disabled) { opacity: 0.92; }
.ai-btn.secondary {
  background: #fff; border: 1.5px solid #7c9db5; color: #5a7d96; margin-top: 4px;
}
.ai-btn.secondary:hover:not(:disabled) { background: rgba(124,157,181,0.06); }
.ai-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.example-list { margin-top: 8px; }
.ex-card { padding: 10px 14px; background: #f8fafb; border-radius: 8px; border: 1px solid #eee; margin-bottom: 6px; }
.ex-s { font-size: 14px; font-weight: 600; color: var(--color-text); }
.ex-t { font-size: 13px; color: #888; margin-top: 2px; }

.check-result { margin-top: 8px; }
.cb { padding: 12px; background: #fef9f9; border-radius: 8px; font-size: 13px; }
.errs { margin-top: 8px; }
.err { padding: 6px 10px; background: #fff; border-radius: 6px; margin: 4px 0; font-size: 12px; display: flex; align-items: center; gap: 6px; }
.eo { color: #e74c3c; text-decoration: line-through; }
.ec { color: #27ae60; font-weight: 600; }
.er { color: #888; font-size: 11px; margin-left: auto; }
.no-err { color: #27ae60; font-weight: 600; font-size: 14px; }

/* 过渡动画 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); }

@media (max-width: 480px) {
  .ai-sidebar { width: 100vw; }
}

.chat-actions { display: flex; justify-content: flex-end; padding: 6px 0; }
.chat-action-btn {
  padding: 4px 12px; border-radius: 6px; border: 1px solid #eee;
  background: transparent; color: #aaa; font-size: 12px; cursor: pointer;
}
.chat-action-btn:hover { background: #f5f5f5; color: #888; }

.sidebar-panel-title { font-size: 14px; font-weight: 700; color: var(--color-text); margin-bottom: 12px; }
.chat-history-list { display: flex; flex-direction: column; gap: 6px; }
.chat-history-list .chat-msg { margin-bottom: 0; }
.chat-history-list .msg-content { font-size: 13px; }
.msg-time { font-size: 11px; color: #ccc; text-align: right; margin-top: 2px; }
.loading-text { text-align: center; padding: 20px; color: #aaa; }
</style>

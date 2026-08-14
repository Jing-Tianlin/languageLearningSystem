import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { API_BASE_URL } from '@/config'
import fetchJson from '@/api/fetchJson'

const BASE = API_BASE_URL

export const useGrammarStore = defineStore('grammar', () => {
  // 状态
  const lang = ref('en')
  const tab = ref('learn') // learn | practice | sentences
  const loading = ref({ lessons: false, practices: false, sentences: false })

  // 教程
  const lessons = ref([])

  // 练习
  const practices = ref([])
  const practiceLevel = ref(0)
  const practiceIndex = ref(0)
  const practiceAnswers = ref({}) // { practiceId: { isCorrect, given } }

  // 长难句
  const sentenceList = ref([])
  const dailyIndex = ref(0)
  // 每日一句 = 从全量列表顺序取，保证可切换
  const displaySentence = computed(() => sentenceList.value[dailyIndex.value] || null)

  // 统计
  const stats = ref({ totalAttempts: 0, correctCount: 0, accuracy: 0 })

  // AI生成题目
  const aiPractices = ref([])
  const aiLoading = ref(false)

  // 计算
  const currentPractices = computed(() => aiPractices.value.length > 0 ? aiPractices.value : practices.value)
  const totalQuestions = computed(() => currentPractices.value.length)
  const correctCount = computed(() => Object.values(practiceAnswers.value).filter(a => a.isCorrect).length)
  const answeredCount = computed(() => Object.keys(practiceAnswers.value).length)
  const hasAIPractices = computed(() => aiPractices.value.length > 0)

  async function generateAIQuestions(targetLevel) {
    aiLoading.value = true
    try {
      const json = await fetchJson(`${BASE}/ai/generate-practices`, {
        method: 'POST',
        body: { langCode: lang.value, level: targetLevel, count: 5 },
      })
      if (json.code === 200 && json.data?.length) {
        // 给AI题目分配临时ID（负数避免冲突）
        aiPractices.value = json.data.map((p, i) => ({
          ...p,
          id: -(i + 1),
          done: false,
          aiGenerated: true,
        }))
        practiceAnswers.value = {}
      }
    } catch (e) {
      aiPractices.value = []
    } finally {
      aiLoading.value = false
    }
  }

  function clearAIPractices() {
    aiPractices.value = []
    practiceAnswers.value = {}
  }

  // === 练习答案校验 ===
  function checkAnswer(question, answer) {
    const ans = (answer || '').trim()
    if (!ans) return null

    const expectedParts = question.answer.split(';').map(a => a.trim().toLowerCase()).filter(Boolean)
    const userParts = ans.toLowerCase().split(/[,;\s]+/).filter(Boolean)

    let isCorrect
    if (expectedParts.length > 1) {
      isCorrect = expectedParts.every(part => userParts.includes(part))
    } else {
      isCorrect = ans.toLowerCase() === expectedParts[0]
    }

    recordAnswer(question.id, isCorrect, ans)
    // AI生成的题目不提交到后端（没有真实practiceId）
    if (!question.aiGenerated) {
      submitPracticeResult(question.id, isCorrect, ans)
    }
    return isCorrect
  }

  // === 教程 ===
  async function fetchLessons() {
    loading.value.lessons = true
    try {
      const json = await fetchJson(`${BASE}/grammar/lessons?langCode=${lang.value}`)
      if (json.code === 200 && json.data?.length) {
        lessons.value = json.data.map(l => ({ ...l, expanded: l.sort_order === 1 }))
      }
    } catch (e) { lessons.value = [] }
    finally { loading.value.lessons = false }
  }

  // === 练习 ===
  async function fetchPractices(targetLevel) {
    loading.value.practices = true
    practiceAnswers.value = {}

    // 将全局 targetLevel 映射到后端等级 0/1/2
    let backendLevel
    if (targetLevel === -1 || targetLevel === null) {
      backendLevel = -1 // 全部
    } else if (targetLevel <= 1) {
      backendLevel = 0
    } else if (targetLevel <= 3) {
      backendLevel = 1
    } else {
      backendLevel = 2
    }

    practiceLevel.value = backendLevel

    try {
      if (backendLevel === -1) {
        // 全部等级：并行加载三个等级
        const [r0, r1, r2] = await Promise.all([
          fetchJson(`${BASE}/grammar/practices?langCode=${lang.value}&level=0`),
          fetchJson(`${BASE}/grammar/practices?langCode=${lang.value}&level=1`),
          fetchJson(`${BASE}/grammar/practices?langCode=${lang.value}&level=2`),
        ])
        const all = [
          ...(r0.data || []),
          ...(r1.data || []),
          ...(r2.data || []),
        ]
        practices.value = all.map(p => ({ ...p, done: false }))
      } else {
        const json = await fetchJson(`${BASE}/grammar/practices?langCode=${lang.value}&level=${backendLevel}`)
        practices.value = (json.data || []).map(p => ({ ...p, done: false }))
      }
    } catch (e) { practices.value = [] }
    finally { loading.value.practices = false }
  }

  function recordAnswer(practiceId, isCorrect, given) {
    practiceAnswers.value[practiceId] = { isCorrect, given }
  }

  async function submitPracticeResult(practiceId, isCorrect, given) {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    try {
      await fetchJson(`${BASE}/grammar/record`, {
        method: 'POST',
        body: { userId: Number(userId), practiceId, isCorrect, answerGiven: given, langCode: lang.value },
      })
    } catch (e) { /* 静默失败 */ }
  }

  async function fetchStats() {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    try {
      const json = await fetchJson(`${BASE}/grammar/stats?userId=${userId}&langCode=${lang.value}`)
      if (json.code === 200 && json.data) stats.value = json.data
    } catch (e) {}
  }

  function resetPractices() {
    practiceAnswers.value = {}
    practices.value = practices.value.map(p => ({ ...p, done: false }))
  }

  // === 长难句 ===
  async function fetchSentences() {
    loading.value.sentences = true
    try {
      const json = await fetchJson(`${BASE}/sentences/list?langCode=${lang.value}&limit=100`)
      if (json.code === 200) {
        sentenceList.value = json.data || []
        // 初始随机一句，之后可顺序切换
        dailyIndex.value = sentenceList.value.length
          ? Math.floor(Math.random() * sentenceList.value.length)
          : 0
      }
    } catch (e) {}
    finally { loading.value.sentences = false }
  }

  // 切换每日一句（上一句 / 下一句，循环）
  function prevDailySentence() {
    if (!sentenceList.value.length) return
    dailyIndex.value = (dailyIndex.value - 1 + sentenceList.value.length) % sentenceList.value.length
  }
  function nextDailySentence() {
    if (!sentenceList.value.length) return
    dailyIndex.value = (dailyIndex.value + 1) % sentenceList.value.length
  }

  // === 语言切换 ===
  function setLang(code) {
    lang.value = code
    if (tab.value === 'learn') fetchLessons()
    else if (tab.value === 'practice') fetchPractices()
    else if (tab.value === 'sentences') fetchSentences()
    fetchStats()
  }

  return {
    lang, tab, loading, lessons,
    practices, practiceLevel, practiceIndex, practiceAnswers,
    dailySentence: displaySentence, dailyIndex, sentenceList,
    prevDailySentence, nextDailySentence,
    stats,
    aiPractices, aiLoading, hasAIPractices,
    currentPractices, totalQuestions, correctCount, answeredCount,
    fetchLessons, fetchPractices, recordAnswer, submitPracticeResult, checkAnswer,
    resetPractices, fetchStats,
    fetchSentences,
    setLang,
    generateAIQuestions, clearAIPractices,
  }
})

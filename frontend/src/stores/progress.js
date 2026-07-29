import { defineStore } from 'pinia'
import { ref } from 'vue'
import { progressApi } from '@/api/progress'

export const useProgressStore = defineStore('progress', () => {
  const progressRecords = ref([])
  const loading = ref(false)
  const total = ref(0)

  async function fetchProgress(params = {}) {
    loading.value = true
    try {
      const data = await progressApi.getProgresses(params)
      progressRecords.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function updateProgress(id, data) {
    await progressApi.updateProgress({ ...data, id })
  }

  const stats = ref({
    totalWords: 0,
    studiedWords: 0,
    masteredWords: 0,
    studyStreak: 0,
  })

  function computeStats() {
    const records = progressRecords.value
    stats.value.totalWords = records.length
    stats.value.studiedWords = records.filter((r) => r.reviewCount > 0).length
    stats.value.masteredWords = records.filter((r) => r.familiarity >= 80).length
  }

  return { progressRecords, loading, total, stats, fetchProgress, updateProgress, computeStats }
})

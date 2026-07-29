import { defineStore } from 'pinia'
import { ref } from 'vue'
import { vocabularyApi } from '@/api/vocabulary'

export const useVocabularyStore = defineStore('vocabulary', () => {
  const vocabularyList = ref([])
  const currentWord = ref(null)
  const loading = ref(false)
  const total = ref(0)

  async function fetchVocabularies(params = {}) {
    loading.value = true
    try {
      const data = await vocabularyApi.getVocabularies(params)
      vocabularyList.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchWordById(id) {
    loading.value = true
    try {
      currentWord.value = await vocabularyApi.getVocabularyById(id)
    } finally {
      loading.value = false
    }
  }

  return { vocabularyList, currentWord, loading, total, fetchVocabularies, fetchWordById }
})

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { languageApi } from '@/api/language'

export const useLanguageStore = defineStore('language', () => {
  const languages = ref([])
  const currentLanguage = ref(null)
  const loading = ref(false)

  async function fetchLanguages(params = {}) {
    loading.value = true
    try {
      const data = await languageApi.getLanguages(params)
      languages.value = data.records || data || []
    } finally {
      loading.value = false
    }
  }

  function setCurrentLanguage(lang) {
    currentLanguage.value = lang
  }

  return { languages, currentLanguage, loading, fetchLanguages, setCurrentLanguage }
})

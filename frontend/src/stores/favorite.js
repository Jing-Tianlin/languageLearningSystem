import { defineStore } from 'pinia'
import { ref } from 'vue'
import { favoriteApi } from '@/api/favorite'

export const useFavoriteStore = defineStore('favorite', () => {
  const favorites = ref([])
  /** 本地收藏 vocabId 集合, O(1) 查重 */
  const favSet = ref(new Set())
  const loading = ref(false)

  async function fetchFavorites(params = {}) {
    loading.value = true
    try {
      const data = await favoriteApi.getFavorites(params)
      favorites.value = data.records || []
      favSet.value = new Set(favorites.value.map(f => f.vocabId))
    } finally {
      loading.value = false
    }
  }

  /** 带重试的 API 调用 */
  async function retry(fn, max = 3, delay = 300) {
    for (let i = 0; i < max; i++) {
      try { return await fn() }
      catch (e) {
        if (i === max - 1) throw e
        await new Promise(r => setTimeout(r, delay * Math.pow(2, i)))
      }
    }
  }

  async function addFavorite(vocabId, langCode) {
    const userId = localStorage.getItem('userId')
    if (!userId) throw new Error('NOT_LOGGED_IN')

    // 乐观更新: 先加入本地状态
    favSet.value.add(vocabId)

    try {
      await retry(() => favoriteApi.addFavorite({ userId: Number(userId), vocabId, langCode }))
    } catch (e) {
      // 回滚
      favSet.value.delete(vocabId)
      throw e
    }
  }

  async function removeFavorite(vocabId) {
    const userId = localStorage.getItem('userId')
    if (!userId) throw new Error('NOT_LOGGED_IN')
    const record = favorites.value.find(f => f.vocabId === vocabId)

    // 乐观更新: 先从本地移除
    favSet.value.delete(vocabId)
    if (record) favorites.value = favorites.value.filter(f => f.vocabId !== vocabId)

    try {
      await retry(() => favoriteApi.removeByVocab(userId, vocabId))
    } catch (e) {
      // 回滚
      if (record) favorites.value.push(record)
      favSet.value.add(vocabId)
      throw e
    }
  }

  function isFavorite(vocabId) {
    return favSet.value.has(vocabId)
  }

  return { favorites, favSet, loading, fetchFavorites, addFavorite, removeFavorite, isFavorite }
})

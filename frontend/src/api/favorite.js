import client from './client'

export const favoriteApi = {
  getFavorites(params) {
    return client.get('/favorite/favorites', { params })
  },
  getFavoriteById(id) {
    return client.get(`/favorite/favorites/${id}`)
  },
  addFavorite(data) {
    return client.post('/favorite/favorites', data)
  },
  removeFavorite(id) {
    return client.delete(`/favorite/favorites/${id}`)
  },
  removeByVocab(userId, vocabId) {
    return client.delete('/favorite/favorites/by-vocab', { params: { userId, vocabId } })
  },
}

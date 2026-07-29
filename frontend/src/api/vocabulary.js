import client from './client'

export const vocabularyApi = {
  getVocabularies(params) {
    return client.get('/vocabulary/vocabularies', { params })
  },
  getVocabularyById(id) {
    return client.get(`/vocabulary/vocabularies/${id}`)
  },
  createVocabulary(data) {
    return client.post('/vocabulary/vocabularies', data)
  },
  updateVocabulary(data) {
    return client.put('/vocabulary/vocabularies', data)
  },
  deleteVocabulary(id) {
    return client.delete(`/vocabulary/vocabularies/${id}`)
  },
  smartSelect(params) {
    return client.get('/vocabulary/smart-select', { params })
  },
  getQuizOptions(params) {
    return client.get('/vocabulary/quiz-options', { params })
  },
  getQuizOptionsBatch(data) {
    return client.post('/vocabulary/quiz-options/batch', data)
  },
  getStats(params) {
    return client.get('/vocabulary/stats', { params })
  },
}

import client from './client'

export const languageApi = {
  getLanguages(params) {
    return client.get('/language/languages', { params })
  },
  getLanguageById(id) {
    return client.get(`/language/languages/${id}`)
  },
  createLanguage(data) {
    return client.post('/language/languages', data)
  },
  updateLanguage(data) {
    return client.put('/language/languages', data)
  },
  deleteLanguage(id) {
    return client.delete(`/language/languages/${id}`)
  },
}

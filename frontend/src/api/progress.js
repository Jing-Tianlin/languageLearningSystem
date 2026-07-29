import client from './client'

export const progressApi = {
  getProgresses(params) {
    return client.get('/progress/progresses', { params })
  },
  getProgressById(id) {
    return client.get(`/progress/progresses/${id}`)
  },
  createProgress(data) {
    return client.post('/progress/progresses', data)
  },
  updateProgress(data) {
    return client.put('/progress/progresses', data)
  },
  deleteProgress(id) {
    return client.delete(`/progress/progresses/${id}`)
  },
  reportHesitation(data) {
    return client.post('/progress/report-hesitation', data)
  },
  getWeakWords(params) {
    return client.get('/progress/weak-words', { params })
  },
  getTodayCount(params) {
    return client.get('/progress/today-count', { params })
  },
}

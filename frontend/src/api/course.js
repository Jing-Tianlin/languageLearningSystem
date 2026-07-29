import client from './client'

export const courseApi = {
  getCourses(params) {
    return client.get('/course/courses', { params })
  },
  getCourseById(id) {
    return client.get(`/course/courses/${id}`)
  },
  createCourse(data) {
    return client.post('/course/courses', data)
  },
  updateCourse(data) {
    return client.put('/course/courses', data)
  },
  deleteCourse(id) {
    return client.delete(`/course/courses/${id}`)
  },
}

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { courseApi } from '@/api/course'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const currentCourse = ref(null)
  const loading = ref(false)
  const total = ref(0)

  async function fetchCourses(params = {}) {
    loading.value = true
    try {
      const data = await courseApi.getCourses(params)
      courses.value = data.records || []
      total.value = data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchCourseById(id) {
    loading.value = true
    try {
      currentCourse.value = await courseApi.getCourseById(id)
    } finally {
      loading.value = false
    }
  }

  return { courses, currentCourse, loading, total, fetchCourses, fetchCourseById }
})

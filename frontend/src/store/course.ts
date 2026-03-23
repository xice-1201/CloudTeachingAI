import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Course, Chapter } from '@/types'
import { courseApi } from '@/api/course'

export const useCourseStore = defineStore('course', () => {
  const courses = ref<Course[]>([])
  const currentCourse = ref<Course | null>(null)
  const chapters = ref<Chapter[]>([])
  const loading = ref(false)

  async function fetchCourses(params?: { page?: number; pageSize?: number; keyword?: string }) {
    loading.value = true
    try {
      const res = await courseApi.listCourses(params)
      courses.value = res.items
      return res
    } finally {
      loading.value = false
    }
  }

  async function fetchCourse(id: string) {
    currentCourse.value = await courseApi.getCourse(id)
    return currentCourse.value
  }

  async function fetchChapters(courseId: string) {
    chapters.value = await courseApi.listChapters(courseId)
    return chapters.value
  }

  function reset() {
    courses.value = []
    currentCourse.value = null
    chapters.value = []
  }

  return { courses, currentCourse, chapters, loading, fetchCourses, fetchCourse, fetchChapters, reset }
})

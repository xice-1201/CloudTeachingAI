import request from '@/utils/request'
import type { Course, Chapter, Resource, PageResponse } from '@/types'

export const courseApi = {
  listCourses: (params?: { page?: number; pageSize?: number; keyword?: string; status?: string }): Promise<PageResponse<Course>> =>
    request.get('/courses', { params }),

  getCourse: (id: string): Promise<Course> =>
    request.get(`/courses/${id}`),

  createCourse: (data: Partial<Course>): Promise<Course> =>
    request.post('/courses', data),

  updateCourse: (id: string, data: Partial<Course>): Promise<Course> =>
    request.put(`/courses/${id}`, data),

  deleteCourse: (id: string): Promise<void> =>
    request.delete(`/courses/${id}`),

  publishCourse: (id: string): Promise<void> =>
    request.post(`/courses/${id}/publish`),

  enrollCourse: (id: string): Promise<void> =>
    request.post(`/courses/${id}/enroll`),

  listEnrolledCourses: (params?: { page?: number; pageSize?: number }): Promise<PageResponse<Course>> =>
    request.get('/courses/enrolled', { params }),

  // 章节
  listChapters: (courseId: string): Promise<Chapter[]> =>
    request.get(`/courses/${courseId}/chapters`),

  createChapter: (courseId: string, data: Partial<Chapter>): Promise<Chapter> =>
    request.post(`/courses/${courseId}/chapters`, data),

  updateChapter: (courseId: string, chapterId: string, data: Partial<Chapter>): Promise<Chapter> =>
    request.put(`/courses/${courseId}/chapters/${chapterId}`, data),

  deleteChapter: (courseId: string, chapterId: string): Promise<void> =>
    request.delete(`/courses/${courseId}/chapters/${chapterId}`),

  // 资源
  listResources: (chapterId: string): Promise<Resource[]> =>
    request.get(`/chapters/${chapterId}/resources`),

  getResource: (resourceId: string): Promise<Resource> =>
    request.get(`/resources/${resourceId}`),

  createResource: (chapterId: string, data: Partial<Resource>): Promise<Resource> =>
    request.post(`/chapters/${chapterId}/resources`, data),

  updateResource: (resourceId: string, data: Partial<Resource>): Promise<Resource> =>
    request.put(`/resources/${resourceId}`, data),

  deleteResource: (resourceId: string): Promise<void> =>
    request.delete(`/resources/${resourceId}`),
}

import request from '@/utils/request'
import type { AxiosRequestConfig } from 'axios'
import type { Course, Chapter, KnowledgePointNode, Resource, ResourceTagSuggestion, PageResponse, Announcement, DiscussionPost } from '@/types'

export const courseApi = {
  listCourses: (params?: { page?: number; pageSize?: number; keyword?: string; status?: string }): Promise<PageResponse<Course>> =>
    request.get('/courses', { params }),

  getCourse: (id: string): Promise<Course> =>
    request.get(`/courses/${id}`),

  listAnnouncements: (courseId: string): Promise<Announcement[]> =>
    request.get(`/courses/${courseId}/announcements`),

  createAnnouncement: (courseId: string, data: { title: string; content: string; pinned?: boolean }): Promise<Announcement> =>
    request.post(`/courses/${courseId}/announcements`, data),

  updateAnnouncement: (announcementId: string, data: { title: string; content: string; pinned?: boolean }): Promise<Announcement> =>
    request.put(`/announcements/${announcementId}`, data),

  deleteAnnouncement: (announcementId: string): Promise<void> =>
    request.delete(`/announcements/${announcementId}`),

  listDiscussions: (courseId: string, params?: { resourceId?: number }, config?: AxiosRequestConfig): Promise<DiscussionPost[]> =>
    request.get(`/courses/${courseId}/discussions`, { ...config, params }),

  createDiscussion: (courseId: string, data: { resourceId?: number; parentId?: number; title?: string; content: string }): Promise<DiscussionPost> =>
    request.post(`/courses/${courseId}/discussions`, data),

  deleteDiscussion: (discussionId: string): Promise<void> =>
    request.delete(`/discussions/${discussionId}`),

  createCourse: (data: Partial<Course>): Promise<Course> =>
    request.post('/courses', data),

  uploadCourseCover: (file: File): Promise<{ url: string }> => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/course-covers', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },

  uploadResourceFile: (
    file: File,
    type: Resource['type'],
    onProgress?: (percent: number) => void,
  ): Promise<{ storageKey: string; fileName: string; size: number }> => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/resource-files', formData, {
      params: { type },
      timeout: 120000,
      onUploadProgress: (event) => {
        if (!event.total || !onProgress) return
        onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)))
      },
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },

  updateCourse: (id: string, data: Partial<Course>): Promise<Course> =>
    request.put(`/courses/${id}`, data),

  deleteCourse: (id: string): Promise<void> =>
    request.delete(`/courses/${id}`),

  publishCourse: (id: string): Promise<Course> =>
    request.post(`/courses/${id}/publish`),

  unpublishCourse: (id: string): Promise<Course> =>
    request.post(`/courses/${id}/unpublish`),

  archiveCourse: (id: string): Promise<Course> =>
    request.post(`/courses/${id}/archive`),

  restoreCourse: (id: string): Promise<Course> =>
    request.post(`/courses/${id}/restore`),

  enrollCourse: (id: string): Promise<void> =>
    request.post(`/courses/${id}/enroll`),

  listEnrolledCourses: (params?: { page?: number; pageSize?: number }): Promise<PageResponse<Course>> =>
    request.get('/courses/enrolled', { params }),

  // 章节
  listChapters: (courseId: string, config?: AxiosRequestConfig): Promise<Chapter[]> =>
    request.get(`/courses/${courseId}/chapters`, config),

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

  listKnowledgePointTree: (params?: { activeOnly?: boolean }): Promise<KnowledgePointNode[]> =>
    request.get('/knowledge-points/tree', { params }),

  createKnowledgePoint: (data: {
    parentId?: number | null
    name: string
    description?: string
    keywords?: string
    nodeType: KnowledgePointNode['nodeType']
    active?: boolean
    orderIndex: number
  }): Promise<KnowledgePointNode> =>
    request.post('/knowledge-points', data),

  updateKnowledgePoint: (knowledgePointId: string, data: {
    parentId?: number | null
    name: string
    description?: string
    keywords?: string
    nodeType: KnowledgePointNode['nodeType']
    active?: boolean
    orderIndex: number
  }): Promise<KnowledgePointNode> =>
    request.put(`/knowledge-points/${knowledgePointId}`, data),

  previewResourceTagSuggestions: (data: {
    title?: string
    description?: string
    type?: Resource['type']
    sourceUrl?: string
    fileName?: string
    file?: File
  }): Promise<ResourceTagSuggestion[]> =>
    data.file
      ? (() => {
          const formData = new FormData()
          if (data.title) formData.append('title', data.title)
          if (data.description) formData.append('description', data.description)
          if (data.type) formData.append('type', data.type)
          if (data.sourceUrl) formData.append('sourceUrl', data.sourceUrl)
          formData.append('fileName', data.fileName || data.file.name)
          formData.append('file', data.file)
          return request.post('/resource-tags/suggestions/preview', formData, {
            timeout: 120000,
            headers: {
              'Content-Type': 'multipart/form-data',
            },
          })
        })()
      : request.post('/resource-tags/suggestions/preview', data),

  getResourceTagSuggestions: (resourceId: string): Promise<ResourceTagSuggestion[]> =>
    request.get(`/resources/${resourceId}/tag-suggestions`),

  confirmResourceTags: (resourceId: string, data: { knowledgePointIds?: number[]; tagLabels?: string[] }): Promise<Resource> =>
    request.patch(`/resources/${resourceId}/tags`, data),

  retryResourceTagging: (resourceId: string): Promise<Resource> =>
    request.post(`/resources/${resourceId}/tagging/retry`),

  createResource: (chapterId: string, data: Partial<Resource>): Promise<Resource> =>
    request.post(`/chapters/${chapterId}/resources`, data),

  updateResource: (resourceId: string, data: Partial<Resource>): Promise<Resource> =>
    request.put(`/resources/${resourceId}`, data),

  deleteResource: (resourceId: string): Promise<void> =>
    request.delete(`/resources/${resourceId}`),
}

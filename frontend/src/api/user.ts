import request from '@/utils/request'
import type { User, PageResponse } from '@/types'

export const userApi = {
  getProfile: (): Promise<User> =>
    request.get('/users/me'),

  updateProfile: (data: Partial<User>): Promise<User> =>
    request.put('/users/me', data),

  uploadAvatar: (file: File): Promise<{ url: string }> => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/users/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  getUserById: (id: string): Promise<User> =>
    request.get(`/users/${id}`),

  listStudents: (params?: { page?: number; pageSize?: number; keyword?: string }): Promise<PageResponse<User>> =>
    request.get('/users/students', { params }),

  listTeachers: (params?: { page?: number; pageSize?: number; keyword?: string }): Promise<PageResponse<User>> =>
    request.get('/users/teachers', { params }),

  applyMentor: (mentorId: string): Promise<void> =>
    request.post(`/users/mentor-relations`, { mentorId }),

  getMentorRelations: (): Promise<{ mentor?: User; students: User[] }> =>
    request.get('/users/mentor-relations'),
}

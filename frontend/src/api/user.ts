import request from '@/utils/request'
import type { User, PageResponse, TeacherRegistrationApplication } from '@/types'

export const userApi = {
  getProfile: (config?: Record<string, any>): Promise<User> =>
    request.get('/users/me', config),

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

  listPendingTeacherRegistrationApplications: (): Promise<TeacherRegistrationApplication[]> =>
    request.get('/admin/teacher-registration-applications'),

  approveTeacherRegistrationApplication: (id: number, data: { reviewerId: number; reviewNote?: string }): Promise<TeacherRegistrationApplication> =>
    request.post(`/admin/teacher-registration-applications/${id}/approve`, data),

  rejectTeacherRegistrationApplication: (id: number, data: { reviewerId: number; reviewNote?: string }): Promise<TeacherRegistrationApplication> =>
    request.post(`/admin/teacher-registration-applications/${id}/reject`, data),

  applyMentor: (mentorId: string | number): Promise<void> =>
    request.post(`/users/mentor-relations`, { mentorId }),

  getMentorRelations: (): Promise<{ mentor?: User; students: User[] }> =>
    request.get('/users/mentor-relations'),
}

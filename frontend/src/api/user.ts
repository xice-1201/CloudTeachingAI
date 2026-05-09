import request from '@/utils/request'
import type { AdminAuditLog, User, PageResponse, TeacherRegistrationApplication, MentorApplication, MentorRelations } from '@/types'

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

  listUsers: (params?: { page?: number; pageSize?: number; keyword?: string; role?: string; active?: boolean }): Promise<PageResponse<User>> =>
    request.get('/admin/users', { params }),

  createUser: (data: { username: string; email: string; password: string; role: User['role'] }): Promise<User> =>
    request.post('/admin/users', data),

  activateUser: (id: string | number): Promise<User> =>
    request.post(`/admin/users/${id}/activate`),

  deactivateUser: (id: string | number): Promise<User> =>
    request.post(`/admin/users/${id}/deactivate`),

  listAuditLogs: (params?: {
    page?: number
    pageSize?: number
    keyword?: string
    action?: string
    targetType?: string
  }): Promise<PageResponse<AdminAuditLog>> =>
    request.get('/admin/audit-logs', { params }),

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

  applyMentor: (mentorId: string | number): Promise<MentorApplication> =>
    request.post(`/users/mentor-relations`, { mentorId }),

  getMentorRelations: (): Promise<MentorRelations> =>
    request.get('/users/mentor-relations'),

  approveMentorApplication: (applicationId: string | number): Promise<MentorApplication> =>
    request.post(`/users/mentor-relations/${applicationId}/approve`),

  rejectMentorApplication: (applicationId: string | number): Promise<MentorApplication> =>
    request.post(`/users/mentor-relations/${applicationId}/reject`),
}

import request from '@/utils/request'
import type { Assignment, Submission, PageResponse } from '@/types'

export const assignApi = {
  listAssignments: (courseId: string, params?: { page?: number; pageSize?: number }): Promise<PageResponse<Assignment>> =>
    request.get(`/courses/${courseId}/assignments`, { params }),

  getAssignment: (id: string): Promise<Assignment> =>
    request.get(`/assignments/${id}`),

  createAssignment: (courseId: string, data: Partial<Assignment>): Promise<Assignment> =>
    request.post(`/courses/${courseId}/assignments`, data),

  updateAssignment: (id: string, data: Partial<Assignment>): Promise<Assignment> =>
    request.put(`/assignments/${id}`, data),

  deleteAssignment: (id: string): Promise<void> =>
    request.delete(`/assignments/${id}`),

  // 提交
  submitAssignment: (assignmentId: string, data: { content: string; attachments?: string[] }): Promise<Submission> =>
    request.post(`/assignments/${assignmentId}/submissions`, data),

  getMySubmission: (assignmentId: string): Promise<Submission> =>
    request.get(`/assignments/${assignmentId}/submissions/me`),

  listSubmissions: (assignmentId: string, params?: { page?: number; pageSize?: number }): Promise<PageResponse<Submission>> =>
    request.get(`/assignments/${assignmentId}/submissions`, { params }),

  reviewSubmission: (submissionId: string, data: { score: number; feedback: string }): Promise<Submission> =>
    request.put(`/submissions/${submissionId}/review`, data),
}

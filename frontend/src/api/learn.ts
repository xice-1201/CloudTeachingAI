import request from '@/utils/request'
import type {
  LearningProgress,
  AbilityMap,
  LearningPath,
  MentorAdviceGenerateResponse,
  CourseProgress,
  TeacherDashboard,
  AbilityTestStartResponse,
  AbilityTestAnswerResponse,
} from '@/types'

export const learnApi = {
  getProgress: (resourceId: string): Promise<LearningProgress> =>
    request.get(`/learn/progress/${resourceId}`),

  updateProgress: (resourceId: string, data: { courseId: number; progress: number; lastPosition?: number }): Promise<LearningProgress> =>
    request.put(`/learn/progress/${resourceId}`, data),

  getCourseProgress: (courseId: string): Promise<CourseProgress> =>
    request.get(`/learn/courses/${courseId}/progress`),

  getAbilityMap: (): Promise<AbilityMap[]> =>
    request.get('/learn/ability-map'),

  getMentoredStudentAbilityMap: (studentId: string | number): Promise<AbilityMap[]> =>
    request.get(`/learn/students/${studentId}/ability-map`),

  generateMentorAdvice: (
    studentId: string | number,
    data: { studentName?: string; teacherInstruction?: string } = {},
  ): Promise<MentorAdviceGenerateResponse> =>
    request.post(`/learn/students/${studentId}/advice/generate`, data, { timeout: 60000 }),

  startAbilityTest: (knowledgePointId: number, questionLimit?: number): Promise<AbilityTestStartResponse> =>
    request.post('/learn/ability-test/start', {
      knowledgePointId,
      ...(questionLimit == null ? {} : { questionLimit }),
    }),

  submitAnswer: (sessionId: number, questionId: number, answer: string): Promise<AbilityTestAnswerResponse> =>
    request.post(`/learn/ability-test/${sessionId}/answer`, { questionId, answer }),

  getLearningPath: (): Promise<LearningPath | null> =>
    request.get('/learn/path'),

  generateLearningPath: (): Promise<LearningPath | null> =>
    request.post('/learn/path/generate'),

  getTeacherDashboard: (): Promise<TeacherDashboard> =>
    request.get('/learn/teacher/dashboard'),
}

import request from '@/utils/request'
import type { LearningProgress, AbilityMap, LearningPath, CourseProgress } from '@/types'

export const learnApi = {
  getProgress: (resourceId: string): Promise<LearningProgress> =>
    request.get(`/learn/progress/${resourceId}`),

  updateProgress: (resourceId: string, data: { courseId: number; progress: number; lastPosition?: number }): Promise<LearningProgress> =>
    request.put(`/learn/progress/${resourceId}`, data),

  getCourseProgress: (courseId: string): Promise<CourseProgress> =>
    request.get(`/learn/courses/${courseId}/progress`),

  getAbilityMap: (studentId?: string): Promise<AbilityMap[]> =>
    request.get('/learn/ability-map', { params: { studentId } }),

  startAbilityTest: (knowledgePointId: string): Promise<{ sessionId: string; question: any }> =>
    request.post('/learn/ability-test/start', { knowledgePointId }),

  submitAnswer: (sessionId: string, questionId: string, answer: string): Promise<{ correct: boolean; nextQuestion?: any; completed?: boolean }> =>
    request.post(`/learn/ability-test/${sessionId}/answer`, { questionId, answer }),

  getLearningPath: (): Promise<LearningPath | null> =>
    request.get('/learn/path'),

  generateLearningPath: (): Promise<LearningPath | null> =>
    request.post('/learn/path/generate'),
}

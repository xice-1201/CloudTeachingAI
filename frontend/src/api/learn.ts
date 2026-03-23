import request from '@/utils/request'
import type { LearningProgress, AbilityMap, LearningPath } from '@/types'

export const learnApi = {
  getProgress: (resourceId: string): Promise<LearningProgress> =>
    request.get(`/learn/progress/${resourceId}`),

  updateProgress: (resourceId: string, data: { progress: number; lastPosition?: number }): Promise<void> =>
    request.put(`/learn/progress/${resourceId}`, data),

  getAbilityMap: (studentId?: string): Promise<AbilityMap[]> =>
    request.get('/learn/ability-map', { params: { studentId } }),

  startAbilityTest: (knowledgePointId: string): Promise<{ sessionId: string; question: any }> =>
    request.post('/learn/ability-test/start', { knowledgePointId }),

  submitAnswer: (sessionId: string, questionId: string, answer: string): Promise<{ correct: boolean; nextQuestion?: any; completed?: boolean }> =>
    request.post(`/learn/ability-test/${sessionId}/answer`, { questionId, answer }),

  getLearningPath: (): Promise<LearningPath> =>
    request.get('/learn/path'),

  generateLearningPath: (): Promise<LearningPath> =>
    request.post('/learn/path/generate'),
}

import request from '@/utils/request'
import type { ChatSession } from '@/types'

export const chatApi = {
  listSessions: (): Promise<ChatSession[]> =>
    request.get('/chat/sessions'),

  getSession: (id: number): Promise<ChatSession> =>
    request.get(`/chat/sessions/${id}`),

  createSession: (): Promise<ChatSession> =>
    request.post('/chat/sessions'),

  deleteSession: (id: number): Promise<void> =>
    request.delete(`/chat/sessions/${id}`),

  // SSE 流式响应
  sendMessage: (sessionId: number, message: string): string =>
    `/api/v1/chat/sessions/${sessionId}/messages?message=${encodeURIComponent(message)}`,
}

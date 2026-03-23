import request from '@/utils/request'
import type { ChatSession } from '@/types'

export const chatApi = {
  listSessions: (): Promise<ChatSession[]> =>
    request.get('/chat/sessions'),

  getSession: (id: string): Promise<ChatSession> =>
    request.get(`/chat/sessions/${id}`),

  createSession: (): Promise<ChatSession> =>
    request.post('/chat/sessions'),

  deleteSession: (id: string): Promise<void> =>
    request.delete(`/chat/sessions/${id}`),

  // SSE 流式响应
  sendMessage: (sessionId: string, message: string): string =>
    `/api/v1/chat/sessions/${sessionId}/messages?message=${encodeURIComponent(message)}`,
}

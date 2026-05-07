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

  buildMessageStreamUrl: (sessionId: number, message: string): string => {
    const params = new URLSearchParams({ message })
    const token = localStorage.getItem('token')
    const rawUser = localStorage.getItem('userInfo')

    if (token) {
      params.set('Authorization', `Bearer ${token}`)
    }

    if (rawUser) {
      try {
        const user = JSON.parse(rawUser) as { id?: number | string }
        if (user.id != null) {
          params.set('userId', String(user.id))
        }
      } catch {
        // Ignore malformed local storage; normal request auth still applies.
      }
    }

    return `/api/v1/chat/sessions/${sessionId}/messages?${params.toString()}`
  },
}

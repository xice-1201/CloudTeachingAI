import request from '@/utils/request'
import type { ChatContext, ChatSession } from '@/types'

export const chatApi = {
  listSessions: (): Promise<ChatSession[]> =>
    request.get('/chat/sessions'),

  getSession: (id: number): Promise<ChatSession> =>
    request.get(`/chat/sessions/${id}`),

  createSession: (context?: ChatContext): Promise<ChatSession> =>
    request.post('/chat/sessions', null, { params: compactContext(context) }),

  deleteSession: (id: number): Promise<void> =>
    request.delete(`/chat/sessions/${id}`),

  buildMessageStreamUrl: (sessionId: number, message: string, context?: ChatContext): string => {
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

    Object.entries(compactContext(context)).forEach(([key, value]) => params.set(key, value))

    return `/api/v1/chat/sessions/${sessionId}/messages?${params.toString()}`
  },
}

function compactContext(context?: ChatContext) {
  return Object.fromEntries(
    Object.entries(context ?? {})
      .filter(([, value]) => value !== undefined && value !== null && String(value).trim())
      .map(([key, value]) => [key, String(value)]),
  )
}

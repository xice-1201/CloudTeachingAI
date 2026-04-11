import request from '@/utils/request'
import type { Notification, PageResponse } from '@/types'

export const notifyApi = {
  listNotifications: (params?: { page?: number; pageSize?: number; read?: boolean }): Promise<PageResponse<Notification>> =>
    request.get('/notifications', { params }),

  markAsRead: (id: string): Promise<void> =>
    request.put(`/notifications/${id}/read`),

  markAllAsRead: (): Promise<void> =>
    request.put('/notifications/read-all'),

  getUnreadCount: (): Promise<{ count: number }> =>
    request.get('/notifications/unread-count', {
      headers: { 'X-Silent-Error': 'true' },
    }),
}
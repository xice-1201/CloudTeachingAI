import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Notification } from '@/types'
import { notifyApi } from '@/api/notify'

export const useNotifyStore = defineStore('notify', () => {
  const notifications = ref<Notification[]>([])
  const unreadCount = ref(0)
  let ws: WebSocket | null = null

  async function fetchUnreadCount() {
    const res = await notifyApi.getUnreadCount()
    unreadCount.value = res.count
  }

  async function fetchNotifications(params?: { page?: number; pageSize?: number }) {
    const res = await notifyApi.listNotifications(params)
    notifications.value = res.items
    return res
  }

  async function markAsRead(id: string) {
    await notifyApi.markAsRead(id)
    const n = notifications.value.find((n) => n.id === id)
    if (n && !n.read) {
      n.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  async function markAllAsRead() {
    await notifyApi.markAllAsRead()
    notifications.value.forEach((n) => (n.read = true))
    unreadCount.value = 0
  }

  function connectWebSocket(userId: string) {
    const token = localStorage.getItem('token')
    ws = new WebSocket(`/ws/notifications?token=${token}&userId=${userId}`)

    ws.onmessage = (event) => {
      const notification: Notification = JSON.parse(event.data)
      notifications.value.unshift(notification)
      unreadCount.value++
    }

    ws.onclose = () => {
      // 30s 后重连
      setTimeout(() => connectWebSocket(userId), 30000)
    }
  }

  function disconnectWebSocket() {
    ws?.close()
    ws = null
  }

  return {
    notifications,
    unreadCount,
    fetchUnreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    connectWebSocket,
    disconnectWebSocket,
  }
})

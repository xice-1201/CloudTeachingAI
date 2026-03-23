import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Notification } from '@/types'
import { notifyApi } from '@/api/notify'
import { ElNotification } from 'element-plus'

export const useNotifyStore = defineStore('notify', () => {
  const notifications = ref<Notification[]>([])
  const unreadCount = ref(0)
  let ws: WebSocket | null = null
  let heartbeatTimer: number | null = null
  let reconnectTimer: number | null = null
  let reconnectAttempts = 0
  const MAX_RECONNECT_ATTEMPTS = 5

  async function fetchUnreadCount() {
    try {
      const res = await notifyApi.getUnreadCount()
      unreadCount.value = res.count
    } catch (error) {
      console.error('Failed to fetch unread count:', error)
    }
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

  function startHeartbeat() {
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = window.setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, 30000) // 每 30 秒发送心跳
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect(userId: string) {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      console.warn('[WebSocket] Max reconnect attempts reached, falling back to polling')
      startPollingFallback()
      return
    }

    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    reconnectTimer = window.setTimeout(() => {
      reconnectAttempts++
      console.log(`[WebSocket] Reconnecting... (attempt ${reconnectAttempts})`)
      connectWebSocket(userId)
    }, delay)
  }

  function startPollingFallback() {
    // 降级为 30 秒轮询
    const pollingTimer = window.setInterval(async () => {
      try {
        await fetchUnreadCount()
      } catch (error) {
        console.error('[Polling] Failed to fetch unread count:', error)
      }
    }, 30000)

    // 保存 timer 以便后续清理
    ;(window as any).__notifyPollingTimer = pollingTimer
  }

  function stopPollingFallback() {
    const pollingTimer = (window as any).__notifyPollingTimer
    if (pollingTimer) {
      clearInterval(pollingTimer)
      delete (window as any).__notifyPollingTimer
    }
  }

  function connectWebSocket(userId: string) {
    const token = localStorage.getItem('token')
    if (!token) {
      console.warn('[WebSocket] No token found, skipping connection')
      return
    }

    const wsUrl = `${import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080'}/ws/notifications?token=${token}`

    try {
      ws = new WebSocket(wsUrl)

      ws.onopen = () => {
        console.log('[WebSocket] Connected')
        reconnectAttempts = 0
        stopPollingFallback()
        startHeartbeat()
      }

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)

          if (data.type === 'pong') {
            // 心跳响应，忽略
            return
          }

          const notification: Notification = data
          notifications.value.unshift(notification)
          unreadCount.value++

          // 显示桌面通知
          ElNotification({
            title: notification.title,
            message: notification.content,
            type: 'info',
            duration: 4500,
          })
        } catch (error) {
          console.error('[WebSocket] Failed to parse message:', error)
        }
      }

      ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error)
      }

      ws.onclose = (event) => {
        console.log('[WebSocket] Disconnected', event.code, event.reason)
        stopHeartbeat()

        // 非正常关闭，尝试重连
        if (event.code !== 1000) {
          scheduleReconnect(userId)
        }
      }
    } catch (error) {
      console.error('[WebSocket] Failed to create connection:', error)
      scheduleReconnect(userId)
    }
  }

  function disconnectWebSocket() {
    stopHeartbeat()
    stopPollingFallback()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      ws.close(1000, 'User logout')
      ws = null
    }
    reconnectAttempts = 0
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

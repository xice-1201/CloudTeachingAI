import axios from 'axios'

export type ServiceHealthStatus = 'UP' | 'DOWN'

export interface ServiceHealthResult {
  key: string
  name: string
  endpoint: string
  status: ServiceHealthStatus
  httpStatus?: number
  responseTimeMs?: number
  checkedAt: string
  message?: string
}

const serviceHealthTargets = [
  { key: 'auth-service', name: '认证服务', endpoint: '/health/auth-service' },
  { key: 'user-service', name: '用户服务', endpoint: '/health/user-service' },
  { key: 'course-service', name: '课程服务', endpoint: '/health/course-service' },
  { key: 'learn-service', name: '学习服务', endpoint: '/health/learn-service' },
  { key: 'assign-service', name: '作业服务', endpoint: '/health/assign-service' },
  { key: 'notify-service', name: '通知服务', endpoint: '/health/notify-service' },
  { key: 'tag-agent', name: 'AI 标注服务', endpoint: '/health/tag-agent' },
  { key: 'chat-agent', name: 'AI 聊天服务', endpoint: '/health/chat-agent' },
] as const

function readHealthStatus(payload: unknown) {
  if (payload && typeof payload === 'object' && 'status' in payload) {
    return String((payload as { status?: unknown }).status ?? '').toUpperCase()
  }
  return ''
}

export const systemApi = {
  listServiceHealth: async (): Promise<ServiceHealthResult[]> => Promise.all(serviceHealthTargets.map(async (target) => {
    const startedAt = performance.now()
    const checkedAt = new Date().toISOString()
    try {
      const response = await axios.get(target.endpoint, {
        timeout: 5000,
        headers: {
          'X-Silent-Error': 'true',
          'X-Skip-Auth-Redirect': 'true',
        },
      })
      const responseTimeMs = Math.round(performance.now() - startedAt)
      const rawStatus = readHealthStatus(response.data)
      const healthy = response.status >= 200 && response.status < 300 && (rawStatus === 'UP' || rawStatus === 'OK')
      return {
        ...target,
        status: healthy ? 'UP' : 'DOWN',
        httpStatus: response.status,
        responseTimeMs,
        checkedAt,
        message: healthy ? undefined : `健康响应异常：${rawStatus || 'UNKNOWN'}`,
      }
    } catch (error) {
      const response = axios.isAxiosError(error) ? error.response : undefined
      return {
        ...target,
        status: 'DOWN',
        httpStatus: response?.status,
        responseTimeMs: Math.round(performance.now() - startedAt),
        checkedAt,
        message: response?.status ? `HTTP ${response.status}` : '服务不可达或请求超时',
      }
    }
  })),
}

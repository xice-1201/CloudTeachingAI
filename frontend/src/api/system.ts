import request from '@/utils/request'

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

export const systemApi = {
  listServiceHealth: (): Promise<ServiceHealthResult[]> =>
    request.get('/admin/system-health', {
      headers: {
        'X-Silent-Error': 'true',
        'X-Skip-Auth-Redirect': 'true',
      },
    }),
}

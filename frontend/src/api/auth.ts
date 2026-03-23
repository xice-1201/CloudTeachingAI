import request from '@/utils/request'
import type { LoginRequest, LoginResponse } from '@/types'

export const authApi = {
  login: (data: LoginRequest): Promise<LoginResponse> =>
    request.post('/auth/login', data),

  logout: (): Promise<void> =>
    request.post('/auth/logout'),

  refreshToken: (): Promise<{ token: string }> =>
    request.post('/auth/refresh'),

  sendResetEmail: (email: string): Promise<void> =>
    request.post('/auth/password/reset-email', { email }),

  resetPassword: (token: string, newPassword: string): Promise<void> =>
    request.post('/auth/password/reset', { token, newPassword }),
}

import request from '@/utils/request'
import type { LoginRequest, LoginResponse } from '@/types'

export const authApi = {
  login: (data: LoginRequest): Promise<LoginResponse> =>
    request.post('/auth/login', data),

  logout: (refreshToken: string): Promise<void> =>
    request.post(`/auth/logout?refreshToken=${encodeURIComponent(refreshToken)}`),

  refreshToken: (refreshToken: string): Promise<LoginResponse> =>
    request.post(`/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`),

  sendResetEmail: (email: string): Promise<void> =>
    request.post('/auth/password/reset-request', { email }),

  resetPassword: (token: string, newPassword: string): Promise<void> =>
    request.post('/auth/password/reset', { token, newPassword }),
}

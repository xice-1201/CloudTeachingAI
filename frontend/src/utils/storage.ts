/**
 * 本地存储工具类
 * 统一管理 localStorage 的 key，避免硬编码
 */

const STORAGE_KEYS = {
  TOKEN: 'token',
  REFRESH_TOKEN: 'refreshToken',
  USER_ROLE: 'userRole',
  USER_INFO: 'userInfo',
} as const

export const storage = {
  // Token 相关
  getToken: (): string | null => localStorage.getItem(STORAGE_KEYS.TOKEN),
  setToken: (token: string) => localStorage.setItem(STORAGE_KEYS.TOKEN, token),
  removeToken: () => localStorage.removeItem(STORAGE_KEYS.TOKEN),

  getRefreshToken: (): string | null => localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN),
  setRefreshToken: (token: string) => localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, token),
  removeRefreshToken: () => localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN),

  // 用户信息
  getUserRole: (): string | null => localStorage.getItem(STORAGE_KEYS.USER_ROLE),
  setUserRole: (role: string) => localStorage.setItem(STORAGE_KEYS.USER_ROLE, role),
  removeUserRole: () => localStorage.removeItem(STORAGE_KEYS.USER_ROLE),

  getUserInfo: (): any | null => {
    const info = localStorage.getItem(STORAGE_KEYS.USER_INFO)
    return info ? JSON.parse(info) : null
  },
  setUserInfo: (info: any) => localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(info)),
  removeUserInfo: () => localStorage.removeItem(STORAGE_KEYS.USER_INFO),

  // 清除所有认证信息
  clearAuth: () => {
    storage.removeToken()
    storage.removeRefreshToken()
    storage.removeUserRole()
    storage.removeUserInfo()
  },
}

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'

const USER_STORAGE_KEY = 'userInfo'

function readStoredUser(): User | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as User
  } catch (_error) {
    localStorage.removeItem(USER_STORAGE_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const user = ref<User | null>(readStoredUser())

  const isLoggedIn = computed(() => !!token.value)
  const isTeacher = computed(() => user.value?.role === 'TEACHER')
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isStudent = computed(() => user.value?.role === 'STUDENT')
  const role = computed(() => user.value?.role ?? localStorage.getItem('userRole'))

  function persistUser(userInfo: User | null) {
    if (userInfo) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(userInfo))
      localStorage.setItem('userRole', userInfo.role)
    } else {
      localStorage.removeItem(USER_STORAGE_KEY)
      localStorage.removeItem('userRole')
    }
  }

  function clearSession() {
    token.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    persistUser(null)
  }

  function hasStoredSession() {
    return !!token.value && !!user.value
  }

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    token.value = res.accessToken
    refreshToken.value = res.refreshToken
    user.value = res.user
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    persistUser(res.user)
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await authApi.logout(refreshToken.value)
      }
    } finally {
      clearSession()
    }
  }

  async function fetchProfile() {
    user.value = await userApi.getProfile()
    persistUser(user.value)
  }

  return {
    token,
    refreshToken,
    user,
    isLoggedIn,
    isTeacher,
    isAdmin,
    isStudent,
    role,
    login,
    logout,
    fetchProfile,
    clearSession,
    hasStoredSession,
  }
})
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isTeacher = computed(() => user.value?.role === 'TEACHER')
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isStudent = computed(() => user.value?.role === 'STUDENT')
  const role = computed(() => user.value?.role ?? localStorage.getItem('userRole'))

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    token.value = res.accessToken
    refreshToken.value = res.refreshToken
    user.value = res.user
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    localStorage.setItem('userRole', res.user.role)
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await authApi.logout(refreshToken.value)
      }
    } finally {
      token.value = null
      refreshToken.value = null
      user.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userRole')
    }
  }

  async function fetchProfile() {
    user.value = await userApi.getProfile()
    if (user.value) {
      localStorage.setItem('userRole', user.value.role)
    }
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
  }
})

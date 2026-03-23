import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isTeacher = computed(() => user.value?.role === 'TEACHER')
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isStudent = computed(() => user.value?.role === 'STUDENT')

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
    localStorage.setItem('userRole', res.user.role)
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      user.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('userRole')
    }
  }

  async function fetchProfile() {
    user.value = await userApi.getProfile()
  }

  return { token, user, isLoggedIn, isTeacher, isAdmin, isStudent, login, logout, fetchProfile }
})

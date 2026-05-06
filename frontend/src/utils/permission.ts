/**
 * 权限检查工具
 */

import { useUserStore } from '@/store/user'

/**
 * 检查用户是否有指定角色
 */
export function hasRole(role: string | string[]): boolean {
  const userStore = useUserStore()
  const userRole = userStore.role

  if (!userRole) return false

  if (Array.isArray(role)) {
    return role.includes(userRole)
  }

  return userRole === role
}

/**
 * 检查用户是否为学生
 */
export function isStudent(): boolean {
  return hasRole('STUDENT')
}

/**
 * 检查用户是否为教师
 */
export function isTeacher(): boolean {
  return hasRole('TEACHER')
}

/**
 * 检查用户是否为管理员
 */
export function isAdmin(): boolean {
  return hasRole('ADMIN')
}

/**
 * 检查用户是否为教师或管理员
 */
export function isTeacherOrAdmin(): boolean {
  return hasRole(['TEACHER', 'ADMIN'])
}

/**
 * 检查用户是否已登录
 */
export function isLoggedIn(): boolean {
  const userStore = useUserStore()
  return userStore.isLoggedIn
}

/**
 * 检查用户是否有权限访问资源
 * @param resourceOwnerId 资源所有者 ID
 * @param allowRoles 允许访问的角色
 */
export function canAccess(resourceOwnerId?: string | number, allowRoles?: string[]): boolean {
  const userStore = useUserStore()

  // 管理员可以访问所有资源
  if (isAdmin()) return true

  // 检查角色权限
  if (allowRoles && !hasRole(allowRoles)) return false

  // 检查资源所有权
  if (resourceOwnerId && String(userStore.user?.id) !== String(resourceOwnerId)) return false

  return true
}

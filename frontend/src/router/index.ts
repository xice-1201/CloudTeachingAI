import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/course/CourseListView.vue'),
      },
      {
        path: 'courses/create',
        name: 'CourseCreate',
        component: () => import('@/views/course/CourseEditView.vue'),
        meta: { roles: ['TEACHER', 'ADMIN'] },
      },
      {
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/CourseDetailView.vue'),
      },
      {
        path: 'courses/:id/edit',
        name: 'CourseEdit',
        component: () => import('@/views/course/CourseEditView.vue'),
        meta: { roles: ['TEACHER', 'ADMIN'] },
      },
      {
        path: 'courses/:courseId/learn/:resourceId',
        name: 'ResourceLearn',
        component: () => import('@/views/learn/ResourceLearnView.vue'),
      },
      {
        path: 'learning',
        name: 'LearningCenter',
        component: () => import('@/views/learn/LearningCenterView.vue'),
      },
      {
        path: 'learning/ability-test',
        name: 'AbilityTest',
        component: () => import('@/views/learn/AbilityTestView.vue'),
      },
      {
        path: 'learning/path',
        name: 'LearningPath',
        component: () => import('@/views/learn/LearningPathView.vue'),
      },
      {
        path: 'assignments',
        name: 'AssignmentList',
        component: () => import('@/views/assign/AssignmentListView.vue'),
      },
      {
        path: 'assignments/:id',
        name: 'AssignmentDetail',
        component: () => import('@/views/assign/AssignmentDetailView.vue'),
      },
      {
        path: 'assignments/:id/submissions',
        name: 'SubmissionList',
        component: () => import('@/views/assign/SubmissionListView.vue'),
        meta: { roles: ['TEACHER', 'ADMIN'] },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/NotificationsView.vue'),
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/ProfileView.vue'),
      },
      {
        path: 'mentor',
        name: 'MentorRelation',
        component: () => import('@/views/user/MentorView.vue'),
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/admin/AdminView.vue'),
        meta: { roles: ['ADMIN'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const token = localStorage.getItem('token')
  const requiresAuth = to.meta.requiresAuth !== false
  const hasStoredSession = userStore.hasStoredSession()

  if (to.name === 'Login' && token && hasStoredSession) {
    next({ name: 'Dashboard' })
    return
  }

  if (to.name === 'Login' && token && !hasStoredSession) {
    userStore.clearSession()
    next()
    return
  }

  if (requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (requiresAuth && token && !hasStoredSession) {
    userStore.clearSession()
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.roles) {
    const userRole = localStorage.getItem('userRole')
    const allowedRoles = to.meta.roles as string[]
    if (!userRole || !allowedRoles.includes(userRole)) {
      next({ name: 'Dashboard' })
      return
    }
  }

  next()
})

export default router

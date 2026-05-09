<template>
  <el-container class="main-layout">
    <el-aside :width="`${sidebarWidth}px`" class="sidebar">
      <div class="logo">
        <span class="logo-text">CloudTeachingAI</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
      >
        <el-menu-item :index="homeMenuPath">
          <el-icon><Odometer /></el-icon>
          <span>{{ homeMenuLabel }}</span>
        </el-menu-item>

        <el-menu-item v-if="!userStore.isAdmin && !userStore.isStudent" index="/courses">
          <el-icon><Reading /></el-icon>
          <span>课程管理</span>
        </el-menu-item>

        <el-sub-menu v-if="userStore.isStudent" index="learning">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>学习中心</span>
          </template>
          <el-menu-item index="/learning">学习概览</el-menu-item>
          <el-menu-item index="/courses">课程管理</el-menu-item>
          <el-menu-item index="/learning/ability-test">能力测试</el-menu-item>
        </el-sub-menu>

        <el-menu-item v-if="!userStore.isAdmin" index="/assignments">
          <el-icon><EditPen /></el-icon>
          <span>作业管理</span>
        </el-menu-item>

        <el-menu-item v-if="!userStore.isAdmin" index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 助手</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.isAdmin" index="/knowledge-graph">
          <el-icon><Share /></el-icon>
          <span>知识图谱</span>
        </el-menu-item>

        <el-menu-item v-if="!userStore.isAdmin" index="/mentor">
          <el-icon><UserFilled /></el-icon>
          <span>导师关系</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.isAdmin" index="/admin">
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <NotificationBell />
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userStore.user?.avatar">
                {{ userStore.user?.username?.[0]?.toUpperCase() }}
              </el-avatar>
              <span class="username">{{ userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notifyStore = useNotifyStore()

const sidebarWidth = 220

const homeMenuPath = computed(() => (userStore.isAdmin ? '/admin' : '/dashboard'))
const homeMenuLabel = computed(() => (userStore.isAdmin ? '管理首页' : '首页'))
const activeMenu = computed(() => {
  if (userStore.isAdmin && route.path.startsWith('/admin')) {
    return '/admin'
  }
  if (route.path.startsWith('/courses')) {
    return '/courses'
  }
  return route.path
})

const breadcrumbs = computed(() => {
  return route.matched
    .filter((r) => r.meta?.title)
    .map((r) => ({ path: r.path, title: r.meta.title as string }))
})

async function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    await userStore.logout()
    router.push('/login')
  }
}

onMounted(async () => {
  if (!userStore.user) await userStore.fetchProfile()
  await notifyStore.fetchUnreadCount()
  if (userStore.user) notifyStore.connectWebSocket(userStore.user.id)
})

onUnmounted(() => {
  notifyStore.disconnectWebSocket()
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #ffffff1a;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.sidebar .el-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #303133;
}

.main-content {
  background: #f5f7fa;
  overflow-y: auto;
}
</style>

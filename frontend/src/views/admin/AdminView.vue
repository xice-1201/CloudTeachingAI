<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">系统管理</span>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users" />
      <el-tab-pane label="教师注册申请" name="teacherApplications" />
      <el-tab-pane label="课程管理" name="courses" />
      <el-tab-pane label="系统设置" name="settings" />
    </el-tabs>

    <el-card shadow="never" style="margin-top: 16px">
      <div v-if="activeTab === 'users'">
        <el-table :data="users" v-loading="userLoading">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="activeTab === 'teacherApplications'">
        <el-table :data="teacherApplications" v-loading="applicationLoading">
          <el-table-column prop="username" label="申请人" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="提交时间" width="180">
            <template #default="{ row }">{{ formatDate(row.requestedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag type="warning">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button type="success" link @click="approveApplication(row.id)">通过</el-button>
              <el-button type="danger" link @click="rejectApplication(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!applicationLoading && teacherApplications.length === 0"
          description="暂无待审批的教师注册申请"
        />
      </div>

      <div v-else-if="activeTab === 'courses'">
        <p class="placeholder-text">课程管理功能开发中...</p>
      </div>

      <div v-else-if="activeTab === 'settings'">
        <p class="placeholder-text">系统设置功能开发中...</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { TeacherRegistrationApplication, User } from '@/types'

const userStore = useUserStore()
const activeTab = ref('users')
const users = ref<User[]>([])
const teacherApplications = ref<TeacherRegistrationApplication[]>([])
const userLoading = ref(false)
const applicationLoading = ref(false)

function roleTagType(role: string) {
  return { STUDENT: 'info', TEACHER: 'success', ADMIN: 'danger' }[role] ?? 'info'
}

function roleLabel(role: string) {
  return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role] ?? role
}

function statusLabel(status: string) {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝' }[status] ?? status
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

async function fetchUsers() {
  userLoading.value = true
  try {
    const [students, teachers] = await Promise.all([
      userApi.listStudents({ page: 1, pageSize: 100 }),
      userApi.listTeachers({ page: 1, pageSize: 100 }),
    ])
    users.value = [...teachers.items, ...students.items]
  } finally {
    userLoading.value = false
  }
}

async function fetchTeacherApplications() {
  applicationLoading.value = true
  try {
    teacherApplications.value = await userApi.listPendingTeacherRegistrationApplications()
  } finally {
    applicationLoading.value = false
  }
}

async function approveApplication(id: number) {
  if (!userStore.user) return

  try {
    await ElMessageBox.confirm('确认通过这条教师注册申请吗？', '审批确认', {
      type: 'warning',
    })

    await userApi.approveTeacherRegistrationApplication(id, {
      reviewerId: userStore.user.id,
    })
    ElMessage.success('教师注册申请已通过')
    await Promise.all([fetchTeacherApplications(), fetchUsers()])
  } catch (_error) {
    // ignore cancel
  }
}

async function rejectApplication(id: number) {
  if (!userStore.user) return

  try {
    await ElMessageBox.confirm('确认拒绝这条教师注册申请吗？', '审批确认', {
      type: 'warning',
    })

    await userApi.rejectTeacherRegistrationApplication(id, {
      reviewerId: userStore.user.id,
    })
    ElMessage.success('教师注册申请已拒绝')
    await fetchTeacherApplications()
  } catch (_error) {
    // ignore cancel
  }
}

watch(activeTab, async (tab) => {
  if (tab === 'users') {
    await fetchUsers()
  }

  if (tab === 'teacherApplications') {
    await fetchTeacherApplications()
  }
})

onMounted(async () => {
  await fetchUsers()
})
</script>

<style scoped>
.placeholder-text {
  color: #909399;
}
</style>

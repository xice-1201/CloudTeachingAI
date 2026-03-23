<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">系统管理</span>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users" />
      <el-tab-pane label="课程管理" name="courses" />
      <el-tab-pane label="系统设置" name="settings" />
    </el-tabs>

    <el-card shadow="never" style="margin-top: 16px">
      <div v-if="activeTab === 'users'">
        <el-table :data="users" v-loading="loading">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default>
              <el-button type="primary" link>编辑</el-button>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="activeTab === 'courses'">
        <p style="color: #909399">课程管理功能开发中...</p>
      </div>

      <div v-else-if="activeTab === 'settings'">
        <p style="color: #909399">系统设置功能开发中...</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { userApi } from '@/api/user'
import type { User } from '@/types'

const activeTab = ref('users')
const users = ref<User[]>([])
const loading = ref(false)

function roleTagType(role: string) {
  return { STUDENT: 'info', TEACHER: 'success', ADMIN: 'danger' }[role] ?? 'info'
}

function roleLabel(role: string) {
  return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role] ?? role
}

function formatDate(d: string) {
  return new Date(d).toLocaleString('zh-CN')
}

async function fetchUsers() {
  loading.value = true
  try {
    const [students, teachers] = await Promise.all([
      userApi.listStudents({ page: 1, pageSize: 100 }),
      userApi.listTeachers({ page: 1, pageSize: 100 }),
    ])
    users.value = [...students.items, ...teachers.items]
  } finally {
    loading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'users') fetchUsers()
})

onMounted(() => {
  if (activeTab.value === 'users') fetchUsers()
})
</script>

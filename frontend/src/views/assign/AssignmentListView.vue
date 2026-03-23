<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">作业管理</span>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待完成" name="pending" />
      <el-tab-pane label="已提交" name="submitted" />
      <el-tab-pane v-if="!userStore.isStudent" label="待批改" name="grading" />
    </el-tabs>

    <div v-loading="loading">
      <el-table :data="assignments" style="width: 100%; margin-top: 16px">
        <el-table-column prop="title" label="作业标题" min-width="200" />
        <el-table-column label="截止时间" width="160">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row.dueDate) }">{{ formatDate(row.dueDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxScore" label="满分" width="80" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/assignments/${row.id}`)">查看</el-button>
            <el-button v-if="!userStore.isStudent" type="primary" link @click="$router.push(`/assignments/${row.id}/submissions`)">
              批改
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && assignments.length === 0" description="暂无作业" />

      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { assignApi } from '@/api/assign'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Assignment } from '@/types'

const userStore = useUserStore()
const assignments = ref<Assignment[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const activeTab = ref('pending')

function formatDate(d: string) { return new Date(d).toLocaleString('zh-CN') }
function isOverdue(d: string) { return new Date(d) < new Date() }

async function fetchData() {
  loading.value = true
  try {
    // Fetch assignments across all enrolled courses
    const coursesRes = await courseApi.listEnrolledCourses({ page: 1, pageSize: 100 })
    const all: Assignment[] = []
    await Promise.all(
      coursesRes.items.map(async (c) => {
        const res = await assignApi.listAssignments(c.id, { page: 1, pageSize: 50 })
        all.push(...res.items)
      })
    )
    assignments.value = all
    total.value = all.length
  } finally {
    loading.value = false
  }
}

watch(activeTab, fetchData)
onMounted(fetchData)
</script>

<style scoped>
.overdue { color: #f56c6c; }
</style>

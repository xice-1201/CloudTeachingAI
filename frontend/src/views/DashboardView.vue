<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
    </div>

    <template v-if="isTeacherView">
      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6" v-for="stat in teacherStats" :key="stat.label">
          <el-card shadow="never">
            <div class="stat-card">
              <el-icon :size="36" :color="stat.color"><component :is="stat.icon" /></el-icon>
              <div class="stat-info">
                <div class="stat-value">{{ stat.value }}</div>
                <div class="stat-label">{{ stat.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="16">
          <el-card shadow="never" header="我的课程">
            <el-table :data="teacherCourses" style="width: 100%">
              <el-table-column prop="title" label="课程名称" />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="最近更新" width="180">
                <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button type="primary" link @click="$router.push(`/courses/${row.id}`)">进入课程</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!teacherLoading && teacherCourses.length === 0" class="empty-tip">暂无课程，可先创建第一门课程。</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" header="快捷操作">
            <div class="quick-actions">
              <div class="quick-action" @click="router.push('/courses/create')">
                <div class="quick-action-title">创建课程</div>
                <div class="quick-action-desc">开始搭建新的教学内容</div>
              </div>
              <div class="quick-action" @click="router.push('/courses')">
                <div class="quick-action-title">课程列表</div>
                <div class="quick-action-desc">查看和维护已有课程</div>
              </div>
              <div class="quick-action" @click="router.push('/assignments')">
                <div class="quick-action-title">作业管理</div>
                <div class="quick-action-desc">查看课程作业与提交情况</div>
              </div>
              <div class="quick-action" @click="router.push('/chat')">
                <div class="quick-action-title">AI 助手</div>
                <div class="quick-action-desc">辅助备课与答疑</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-else>
      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6" v-for="stat in studentStats" :key="stat.label">
          <el-card shadow="never">
            <div class="stat-card">
              <el-icon :size="36" :color="stat.color"><component :is="stat.icon" /></el-icon>
              <div class="stat-info">
                <div class="stat-value">{{ stat.value }}</div>
                <div class="stat-label">{{ stat.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="16">
          <el-card shadow="never" header="最近学习">
            <el-table :data="recentCourses" style="width: 100%">
              <el-table-column prop="title" label="课程名称" />
              <el-table-column prop="teacherName" label="教师" width="120" />
              <el-table-column label="进度" width="160">
                <template #default="{ row }">
                  <el-progress :percentage="row.progress" :stroke-width="8" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" link @click="$router.push(`/courses/${row.id}`)">继续学习</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!studentLoading && recentCourses.length === 0" class="empty-tip">暂无已选课程。</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" header="待完成作业">
            <div v-if="pendingAssignments.length === 0" class="empty-tip">暂无待完成作业。</div>
            <div v-for="assignment in pendingAssignments" :key="assignment.id" class="assignment-item" @click="$router.push(`/assignments/${assignment.id}`)">
              <div class="assignment-title">{{ assignment.title }}</div>
              <div class="assignment-due">截止：{{ formatDate(assignment.dueDate) }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, EditPen, TrendCharts, ChatDotRound, Collection, Promotion, Document } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Assignment, Course } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const isTeacherView = computed(() => userStore.isTeacher)
const pageTitle = computed(() => (isTeacherView.value ? '教师首页' : '学习首页'))

const recentCourses = ref<Array<Course & { progress: number }>>([])
const pendingAssignments = ref<Assignment[]>([])
const teacherCourses = ref<Course[]>([])
const studentLoading = ref(false)
const teacherLoading = ref(false)

const studentStats = ref([
  { label: '已选课程', value: 0, icon: Reading, color: '#409eff' },
  { label: '学习进度', value: '0%', icon: TrendCharts, color: '#67c23a' },
  { label: '待完成作业', value: 0, icon: EditPen, color: '#e6a23c' },
  { label: 'AI 对话次数', value: 0, icon: ChatDotRound, color: '#909399' },
])

const teacherStats = ref([
  { label: '我的课程', value: 0, icon: Collection, color: '#409eff' },
  { label: '已发布课程', value: 0, icon: Promotion, color: '#67c23a' },
  { label: '草稿课程', value: 0, icon: EditPen, color: '#e6a23c' },
  { label: '归档课程', value: 0, icon: Document, color: '#909399' },
])

function formatDate(date: string) {
  return new Date(date).toLocaleDateString('zh-CN')
}

function formatDateTime(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

function statusLabel(status: string) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status
}

function statusTagType(status: string) {
  return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info'
}

async function loadStudentDashboard() {
  studentLoading.value = true
  try {
    const response = await courseApi.listEnrolledCourses({ page: 1, pageSize: 5 })
    recentCourses.value = response.items.map((course) => ({
      ...course,
      progress: Math.floor(Math.random() * 100),
    }))

    studentStats.value[0].value = response.total

    if (recentCourses.value.length > 0) {
      const averageProgress = Math.round(
        recentCourses.value.reduce((sum, course) => sum + course.progress, 0) / recentCourses.value.length,
      )
      studentStats.value[1].value = `${averageProgress}%`
    }
  } finally {
    studentLoading.value = false
  }
}

async function loadTeacherDashboard() {
  teacherLoading.value = true
  try {
    const response = await courseApi.listCourses({ page: 1, pageSize: 5 })
    teacherCourses.value = response.items

    teacherStats.value[0].value = response.total
    teacherStats.value[1].value = response.items.filter((course) => course.status === 'PUBLISHED').length
    teacherStats.value[2].value = response.items.filter((course) => course.status === 'DRAFT').length
    teacherStats.value[3].value = response.items.filter((course) => course.status === 'ARCHIVED').length
  } finally {
    teacherLoading.value = false
  }
}

onMounted(async () => {
  if (isTeacherView.value) {
    await loadTeacherDashboard()
    return
  }

  await loadStudentDashboard()
})
</script>

<style scoped>
.stat-cards {
  margin-bottom: 4px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.assignment-item,
.quick-action {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}

.assignment-item:last-child,
.quick-action:last-child {
  border-bottom: none;
}

.assignment-title,
.quick-action-title {
  font-size: 14px;
  color: #303133;
}

.assignment-due,
.quick-action-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.empty-tip {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
}

.quick-actions {
  display: flex;
  flex-direction: column;
}
</style>
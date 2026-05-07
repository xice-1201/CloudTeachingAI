<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
    </div>

    <template v-if="isTeacherView">
      <el-row :gutter="20" class="stat-cards">
        <el-col :xs="24" :sm="12" :lg="6" v-for="stat in teacherStats" :key="stat.label">
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

      <el-row :gutter="20" class="dashboard-row">
        <el-col :xs="24" :lg="16">
          <el-card shadow="never" header="课程分析">
            <el-table :data="teacherDashboard?.courses ?? []" style="width: 100%" v-loading="teacherLoading">
              <el-table-column prop="courseTitle" label="课程名称" min-width="180" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.courseStatus)">{{ statusLabel(row.courseStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalResources" label="资源数" width="90" />
              <el-table-column prop="activeStudents" label="活跃学生" width="100" />
              <el-table-column label="平均进度" width="140">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.averageProgress * 100)" :stroke-width="8" />
                </template>
              </el-table-column>
              <el-table-column label="完成率" width="110">
                <template #default="{ row }">{{ Math.round(row.completionRate * 100) }}%</template>
              </el-table-column>
              <el-table-column label="最热资源" min-width="180">
                <template #default="{ row }">
                  <span>{{ row.hottestResourceTitle || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="最近学习" width="170">
                <template #default="{ row }">{{ row.lastLearnedAt ? formatDateTime(row.lastLearnedAt) : '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110">
                <template #default="{ row }">
                  <el-button type="primary" link @click="$router.push(`/courses/${row.courseId}`)">进入课程</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!teacherLoading && (teacherDashboard?.courses.length ?? 0) === 0" class="empty-tip">暂无课程，可先创建第一门课程。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <el-card shadow="never" header="学生健康度" class="health-card">
            <div v-if="!teacherDashboard || teacherDashboard.studentRisks.length === 0" class="empty-tip">
              暂无可分析的学习风险数据。
            </div>
            <div v-else class="risk-list">
              <div v-for="item in teacherDashboard.studentRisks" :key="item.courseId" class="risk-item">
                <div class="risk-header">
                  <div class="risk-title">{{ item.courseTitle }}</div>
                  <el-tag :type="riskTagType(item.riskLevel)" size="small">{{ riskLabel(item.riskLevel) }}</el-tag>
                </div>
                <div class="risk-insight">{{ item.insight }}</div>
                <div class="risk-meta">
                  <span>活跃 {{ item.activeStudents }}</span>
                  <span>低进度 {{ item.lowProgressStudents }}</span>
                  <span>停滞 {{ item.inactiveStudents }}</span>
                </div>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" header="薄弱知识点">
            <div v-if="!teacherDashboard || teacherDashboard.weakKnowledgePoints.length === 0" class="empty-tip">
              目前还没有足够的学习数据形成知识点分析。
            </div>
            <div v-else class="knowledge-list">
              <div v-for="item in teacherDashboard.weakKnowledgePoints" :key="item.knowledgePointId" class="knowledge-item">
                <div class="knowledge-name">{{ item.knowledgePointName }}</div>
                <div class="knowledge-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
                <el-progress :percentage="Math.round(item.averageProgress * 100)" :stroke-width="8" status="warning" />
                <div class="knowledge-meta">
                  <span>活跃学生 {{ item.activeStudents }}</span>
                  <span>相关资源 {{ item.relatedResources }}</span>
                </div>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" header="快捷操作" class="quick-card">
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
import { Reading, EditPen, TrendCharts, ChatDotRound, Collection, Promotion, Document, DataAnalysis } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { assignApi } from '@/api/assign'
import { useUserStore } from '@/store/user'
import type { Assignment, Course, TeacherDashboard } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const isTeacherView = computed(() => userStore.isTeacher)
const pageTitle = computed(() => (isTeacherView.value ? '教师首页' : '学习首页'))

const recentCourses = ref<Array<Course & { progress: number }>>([])
const pendingAssignments = ref<Assignment[]>([])
const studentLoading = ref(false)
const teacherLoading = ref(false)
const teacherDashboard = ref<TeacherDashboard | null>(null)

const studentStats = ref([
  { label: '已选课程', value: 0, icon: Reading, color: '#409eff' },
  { label: '学习进度', value: '0%', icon: TrendCharts, color: '#67c23a' },
  { label: '待完成作业', value: 0, icon: EditPen, color: '#e6a23c' },
  { label: 'AI 对话次数', value: 0, icon: ChatDotRound, color: '#909399' },
])

const teacherStats = computed(() => {
  const dashboard = teacherDashboard.value
  return [
    { label: '我的课程', value: dashboard?.totalCourses ?? 0, icon: Collection, color: '#409eff' },
    { label: '已发布课程', value: dashboard?.publishedCourses ?? 0, icon: Promotion, color: '#67c23a' },
    { label: '资源总数', value: dashboard?.totalResources ?? 0, icon: Document, color: '#e6a23c' },
    { label: '平均进度', value: `${Math.round((dashboard?.averageProgress ?? 0) * 100)}%`, icon: DataAnalysis, color: '#909399' },
  ]
})

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

function riskLabel(level: string) {
  return { HIGH: '高风险', MEDIUM: '需关注', LOW: '稳定', NO_DATA: '暂无数据' }[level] ?? level
}

function riskTagType(level: string) {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success', NO_DATA: 'info' }[level] ?? 'info'
}

async function loadStudentDashboard() {
  studentLoading.value = true
  try {
    const response = await courseApi.listEnrolledCourses({ page: 1, pageSize: 100 })
    const coursesWithProgress = await Promise.all(
      response.items.map(async (course) => {
        const progress = await learnApi.getCourseProgress(String(course.id)).catch(() => ({
          courseId: course.id,
          progress: 0,
          totalResources: 0,
          completedResources: 0,
          lastLearnedAt: null,
        }))

        return {
          ...course,
          progress: Math.round(progress.progress * 100),
          lastLearnedAt: progress.lastLearnedAt,
        }
      }),
    )

    recentCourses.value = coursesWithProgress
      .sort((a, b) => {
        const aTime = a.lastLearnedAt ? new Date(a.lastLearnedAt).getTime() : 0
        const bTime = b.lastLearnedAt ? new Date(b.lastLearnedAt).getTime() : 0
        return bTime - aTime
      })
      .slice(0, 5)

    studentStats.value[0].value = response.total
    const pending = await assignApi.listPendingAssignments({ pageSize: 100 }).catch(() => [])
    pendingAssignments.value = pending.slice(0, 5)
    studentStats.value[2].value = pending.length

    if (coursesWithProgress.length > 0) {
      const averageProgress = Math.round(
        coursesWithProgress.reduce((sum, course) => sum + course.progress, 0) / coursesWithProgress.length,
      )
      studentStats.value[1].value = `${averageProgress}%`
    } else {
      studentStats.value[1].value = '0%'
    }
  } finally {
    studentLoading.value = false
  }
}

async function loadTeacherDashboard() {
  teacherLoading.value = true
  try {
    teacherDashboard.value = await learnApi.getTeacherDashboard()
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

.dashboard-row {
  margin-top: 20px;
}

.health-card {
  margin-bottom: 20px;
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

.quick-actions,
.knowledge-list,
.risk-list {
  display: flex;
  flex-direction: column;
}

.risk-item + .risk-item {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #f0f2f5;
}

.risk-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.risk-title {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow-wrap: anywhere;
}

.risk-insight {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.risk-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.quick-card {
  margin-top: 20px;
}

.knowledge-item + .knowledge-item {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.knowledge-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.knowledge-path {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.knowledge-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>

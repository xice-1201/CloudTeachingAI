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

      <KnowledgeGraphView embedded />

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
          <el-card shadow="never" header="待处理事项" class="todo-card" v-loading="teacherTodoLoading">
            <div v-if="!teacherTodoLoading && teacherTodos.length === 0" class="empty-tip">
              暂无待处理事项，课堂运行状态良好。
            </div>
            <div v-else class="todo-list">
              <button
                v-for="item in teacherTodos"
                :key="item.id"
                class="todo-item"
                type="button"
                @click="openTeacherTodo(item)"
              >
                <div class="todo-icon">
                  <el-icon><component :is="todoIcon(item.kind)" /></el-icon>
                </div>
                <div class="todo-main">
                  <div class="todo-head">
                    <span class="todo-title">{{ item.title }}</span>
                    <el-tag :type="todoTagType(item.priority)" size="small">{{ item.count }}</el-tag>
                  </div>
                  <div class="todo-desc">{{ item.description }}</div>
                  <div class="todo-meta">{{ item.courseTitle }}</div>
                </div>
              </button>
            </div>
            <div v-if="teacherTodos.length > 0" class="todo-summary">
              共 {{ teacherTodoSummary.total }} 项待处理，优先关注 {{ teacherTodoSummary.urgent }} 项高优先级事项
            </div>
          </el-card>
          <el-card id="student-health" shadow="never" header="学生健康度" class="health-card">
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

      <KnowledgeGraphView embedded />
      <LearningPathView embedded />

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
import { Reading, EditPen, TrendCharts, ChatDotRound, Collection, Promotion, Document, DataAnalysis, Bell, Warning } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { assignApi } from '@/api/assign'
import { useUserStore } from '@/store/user'
import KnowledgeGraphView from '@/views/learn/KnowledgeGraphView.vue'
import LearningPathView from '@/views/learn/LearningPathView.vue'
import type { Assignment, Course, DiscussionPost, Submission, TeacherDashboard } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const isTeacherView = computed(() => userStore.isTeacher)
const pageTitle = computed(() => (isTeacherView.value ? '教师首页' : '学习首页'))

const recentCourses = ref<Array<Course & { progress: number }>>([])
const pendingAssignments = ref<Assignment[]>([])
const studentLoading = ref(false)
const teacherLoading = ref(false)
const teacherTodoLoading = ref(false)
const teacherDashboard = ref<TeacherDashboard | null>(null)

type TeacherTodoKind = 'AI_REVIEW' | 'NEW_SUBMISSION' | 'DISCUSSION' | 'STUDENT_RISK'
type TeacherTodoPriority = 'HIGH' | 'MEDIUM' | 'LOW'

interface TeacherTodoItem {
  id: string
  kind: TeacherTodoKind
  priority: TeacherTodoPriority
  title: string
  description: string
  courseTitle: string
  count: number
  target: string
  sortWeight: number
}

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

const teacherTodos = ref<TeacherTodoItem[]>([])

const teacherTodoSummary = computed(() => ({
  total: teacherTodos.value.reduce((sum, item) => sum + item.count, 0),
  urgent: teacherTodos.value.filter((item) => item.priority === 'HIGH').reduce((sum, item) => sum + item.count, 0),
}))

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

function todoTagType(priority: TeacherTodoPriority) {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[priority] ?? 'info'
}

function todoIcon(kind: TeacherTodoKind) {
  return {
    AI_REVIEW: DataAnalysis,
    NEW_SUBMISSION: EditPen,
    DISCUSSION: ChatDotRound,
    STUDENT_RISK: Warning,
  }[kind] ?? Bell
}

function openTeacherTodo(item: TeacherTodoItem) {
  router.push(item.target)
  if (item.kind === 'STUDENT_RISK') {
    setTimeout(() => document.querySelector('.health-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
  }
}

function countSubmissions(submissions: Submission[], statuses: Submission['status'][]) {
  return submissions.filter((submission) => statuses.includes(submission.status)).length
}

function buildDiscussionTodos(courseId: number, courseTitle: string, discussions: DiscussionPost[]) {
  const unanswered = discussions.filter((post) => !post.parentId && (post.replies?.length ?? 0) === 0)
  const replied = discussions.filter((post) => !post.parentId && (post.replies?.length ?? 0) > 0)
  const todos: TeacherTodoItem[] = []

  if (unanswered.length > 0) {
    todos.push({
      id: `discussion-unanswered-${courseId}`,
      kind: 'DISCUSSION',
      priority: 'MEDIUM',
      title: '待回应的课程讨论',
      description: `${unanswered.length} 个讨论主题还没有回复`,
      courseTitle,
      count: unanswered.length,
      target: `/courses/${courseId}#discussions`,
      sortWeight: 65,
    })
  }

  if (replied.length > 0) {
    todos.push({
      id: `discussion-replied-${courseId}`,
      kind: 'DISCUSSION',
      priority: 'LOW',
      title: '课程讨论有新互动',
      description: `${replied.reduce((sum, post) => sum + (post.replies?.length ?? 0), 0)} 条讨论回复可查看`,
      courseTitle,
      count: replied.length,
      target: `/courses/${courseId}#discussions`,
      sortWeight: 30,
    })
  }

  return todos
}

async function buildAssignmentTodos(courseId: number, courseTitle: string) {
  const assignmentResponse = await assignApi.listAssignments(String(courseId), { page: 1, pageSize: 20 }).catch(() => null)
  if (!assignmentResponse?.items.length) return []

  const submissionsByAssignment = await Promise.all(
    assignmentResponse.items.map(async (assignment) => {
      const response = await assignApi.listSubmissions(String(assignment.id), { page: 1, pageSize: 100 }).catch(() => null)
      return { assignment, submissions: response?.items ?? [] }
    }),
  )

  return submissionsByAssignment.flatMap(({ assignment, submissions }) => {
    const aiReviewCount = countSubmissions(submissions, ['AI_GRADED'])
    const manualCount = countSubmissions(submissions, ['SUBMITTED', 'PENDING_MANUAL', 'GRADING_FAILED'])
    const todos: TeacherTodoItem[] = []

    if (aiReviewCount > 0) {
      todos.push({
        id: `assignment-ai-${assignment.id}`,
        kind: 'AI_REVIEW',
        priority: 'HIGH',
        title: '待复核的 AI 批改',
        description: `${assignment.title} 有 ${aiReviewCount} 份 AI 批改结果待确认`,
        courseTitle,
        count: aiReviewCount,
        target: `/assignments/${assignment.id}/submissions`,
        sortWeight: 100,
      })
    }

    if (manualCount > 0) {
      todos.push({
        id: `assignment-new-${assignment.id}`,
        kind: 'NEW_SUBMISSION',
        priority: 'MEDIUM',
        title: '有新提交的作业',
        description: `${assignment.title} 有 ${manualCount} 份提交需要处理`,
        courseTitle,
        count: manualCount,
        target: `/assignments/${assignment.id}/submissions`,
        sortWeight: 75,
      })
    }

    return todos
  })
}

async function loadTeacherTodos(dashboard: TeacherDashboard) {
  teacherTodoLoading.value = true
  try {
    const riskTodos = dashboard.studentRisks
      .filter((risk) => risk.riskLevel === 'HIGH' || risk.riskLevel === 'MEDIUM')
      .map<TeacherTodoItem>((risk) => ({
        id: `student-risk-${risk.courseId}`,
        kind: 'STUDENT_RISK',
        priority: risk.riskLevel === 'HIGH' ? 'HIGH' : 'MEDIUM',
        title: '高风险学生课程提醒',
        description: risk.insight,
        courseTitle: risk.courseTitle,
        count: risk.lowProgressStudents + risk.inactiveStudents,
        target: '/dashboard#student-health',
        sortWeight: risk.riskLevel === 'HIGH' ? 90 : 60,
      }))

    const courseTodos = await Promise.all(
      dashboard.courses.slice(0, 8).map(async (course) => {
        const [assignmentTodos, discussions] = await Promise.all([
          buildAssignmentTodos(course.courseId, course.courseTitle),
          courseApi.listDiscussions(String(course.courseId)).catch(() => []),
        ])
        return [...assignmentTodos, ...buildDiscussionTodos(course.courseId, course.courseTitle, discussions)]
      }),
    )

    teacherTodos.value = [...riskTodos, ...courseTodos.flat()]
      .filter((item) => item.count > 0)
      .sort((a, b) => b.sortWeight - a.sortWeight || b.count - a.count)
      .slice(0, 10)
  } finally {
    teacherTodoLoading.value = false
  }
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
    await loadTeacherTodos(teacherDashboard.value)
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

.todo-card,
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

.todo-list {
  display: grid;
  gap: 10px;
}

.todo-item {
  width: 100%;
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.todo-item:hover {
  background: #f5f7fa;
}

.todo-icon {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #409eff;
  background: #ecf5ff;
}

.todo-main {
  min-width: 0;
  flex: 1;
}

.todo-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.todo-title {
  min-width: 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.todo-desc {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.todo-meta,
.todo-summary {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.todo-summary {
  padding-top: 10px;
  border-top: 1px solid #f0f2f5;
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

<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">导师关系</span>
    </div>

    <template v-if="isTeacherView">
      <el-row :gutter="20">
        <el-col :xs="24" :lg="14">
          <el-card id="mentor-applications" shadow="never" header="待处理申请" v-loading="relationLoading">
            <div v-if="applications.length === 0" class="empty-tip">暂无待处理导师申请。</div>
            <div v-for="application in applications" :key="application.id" class="application-item">
              <el-avatar :size="44" :src="application.student?.avatar">
                {{ application.student?.username?.[0]?.toUpperCase() }}
              </el-avatar>
              <div class="application-main">
                <div class="application-title">{{ application.student?.username || '未知学生' }}</div>
                <div class="application-meta">{{ application.student?.email || '-' }}</div>
                <div class="application-meta">申请时间：{{ formatDateTime(application.requestedAt) }}</div>
              </div>
              <div class="application-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="handlingApplicationId === application.id"
                  @click="approveApplication(application.id)"
                >
                  同意
                </el-button>
                <el-button
                  size="small"
                  :loading="handlingApplicationId === application.id"
                  @click="rejectApplication(application.id)"
                >
                  拒绝
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card shadow="never" header="我的学生" v-loading="relationLoading">
            <div v-if="students.length === 0" class="empty-tip">暂无已确认的指导学生。</div>
            <div
              v-for="student in students"
              :key="student.id"
              class="teacher-item clickable-student"
              role="button"
              tabindex="0"
              @click="openStudent(student.id)"
              @keydown.enter="openStudent(student.id)"
            >
              <el-avatar :size="40" :src="student.avatar">{{ student.username?.[0]?.toUpperCase() }}</el-avatar>
              <div class="teacher-info">
                <div class="teacher-name">{{ student.username }}</div>
                <div class="teacher-email">{{ student.email }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-else>
      <el-row :gutter="20">
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" header="我的导师" v-loading="relationLoading">
            <template v-if="mentor">
              <div class="mentor-card">
                <el-avatar :size="64" :src="mentor.avatar">
                  {{ mentor.username?.[0]?.toUpperCase() }}
                </el-avatar>
                <div class="mentor-info">
                  <div class="mentor-name">{{ mentor.username }}</div>
                  <div class="mentor-email">{{ mentor.email }}</div>
                  <el-tag class="mentor-status" size="small" type="success">导师关系已确认</el-tag>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无导师" :image-size="80" />
          </el-card>

          <el-card v-if="!hasMentor && applications.length > 0" shadow="never" header="申请进度" class="pending-card">
            <div v-for="application in applications" :key="application.id" class="pending-item">
              <div>
                <div class="teacher-name">{{ application.mentor?.username || '未知教师' }}</div>
                <div class="teacher-email">{{ application.mentor?.email || '-' }}</div>
              </div>
              <el-tag type="warning">等待处理</el-tag>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="14">
          <el-card v-if="!hasMentor" shadow="never" header="申请导师">
            <el-form inline>
              <el-form-item>
                <el-input v-model="keyword" placeholder="搜索教师" clearable @keyup.enter="searchTeachers" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="loading" @click="searchTeachers">搜索</el-button>
              </el-form-item>
            </el-form>

            <div v-loading="loading" class="teacher-list">
              <div v-for="teacher in teachers" :key="teacher.id" class="teacher-item">
                <el-avatar :size="40" :src="teacher.avatar">{{ teacher.username?.[0]?.toUpperCase() }}</el-avatar>
                <div class="teacher-info">
                  <div class="teacher-name">{{ teacher.username }}</div>
                  <div class="teacher-email">{{ teacher.email }}</div>
                </div>
                <el-button
                  size="small"
                  :disabled="Boolean(mentor) || pendingMentorIds.has(teacher.id)"
                  :loading="applyingMentorId === teacher.id"
                  @click="applyMentor(teacher.id)"
                >
                  {{ pendingMentorIds.has(teacher.id) ? '已申请' : '申请' }}
                </el-button>
              </div>
              <el-empty v-if="!loading && teachers.length === 0" description="暂无结果" :image-size="60" />
            </div>
          </el-card>
          <el-card v-else shadow="never" header="向导师提问">
            <div class="confirmed-tip">
              <el-tag type="success">已确认</el-tag>
              <span>可以把学习困惑发送给导师，导师会通过平台消息回复你。</span>
            </div>
            <el-input
              v-model="questionContent"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="输入你想咨询导师的问题，例如学习卡点、测试结果疑问或下一步学习安排"
            />
            <div class="question-actions">
              <el-button type="primary" :loading="questionSubmitting" @click="submitMentorQuestion">
                发送给导师
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { MentorApplication, User } from '@/types'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const mentor = ref<User | null>(null)
const students = ref<User[]>([])
const applications = ref<MentorApplication[]>([])
const teachers = ref<User[]>([])
const keyword = ref('')
const loading = ref(false)
const relationLoading = ref(false)
const applyingMentorId = ref<number | null>(null)
const handlingApplicationId = ref<number | null>(null)
const questionContent = ref('')
const questionSubmitting = ref(false)

const pendingMentorIds = computed(() => new Set(applications.value.map((item) => item.mentorId)))
const currentRole = computed(() => userStore.user?.role ?? localStorage.getItem('userRole'))
const isTeacherView = computed(() => currentRole.value === 'TEACHER')
const isStudentView = computed(() => currentRole.value === 'STUDENT')
const hasMentor = computed(() => Boolean(mentor.value))

function normalizeMentor(value?: User | null) {
  if (!value || value.id == null) return null
  const mentorId = Number(value.id)
  return Number.isFinite(mentorId) ? { ...value, id: mentorId } : null
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

function openStudent(studentId: number) {
  router.push(`/mentor/students/${studentId}`)
}

async function searchTeachers() {
  loading.value = true
  try {
    const res = await userApi.listTeachers({ page: 1, pageSize: 10, keyword: keyword.value })
    teachers.value = res.items
  } finally {
    loading.value = false
  }
}

async function loadRelations() {
  relationLoading.value = true
  try {
    const relations = await userApi.getMentorRelations()
    mentor.value = normalizeMentor(relations.mentor)
    students.value = relations.students ?? []
    applications.value = relations.applications ?? []
    if (mentor.value) {
      teachers.value = []
    }
  } finally {
    relationLoading.value = false
  }
}

async function applyMentor(mentorId: number) {
  if (hasMentor.value) {
    ElMessage.warning('你已经拥有导师关系，不能重复申请')
    return
  }
  applyingMentorId.value = mentorId
  try {
    const application = await userApi.applyMentor(mentorId)
    applications.value = [application, ...applications.value]
    ElMessage.success('申请已发送，等待教师处理')
  } finally {
    applyingMentorId.value = null
  }
}

async function approveApplication(applicationId: number) {
  handlingApplicationId.value = applicationId
  try {
    await userApi.approveMentorApplication(applicationId)
    ElMessage.success('已同意导师申请')
    await loadRelations()
  } finally {
    handlingApplicationId.value = null
  }
}

async function rejectApplication(applicationId: number) {
  handlingApplicationId.value = applicationId
  try {
    await userApi.rejectMentorApplication(applicationId)
    ElMessage.success('已拒绝导师申请')
    await loadRelations()
  } finally {
    handlingApplicationId.value = null
  }
}

async function submitMentorQuestion() {
  const content = questionContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入要咨询导师的问题')
    return
  }
  questionSubmitting.value = true
  try {
    await userApi.askMentorQuestion(content)
    questionContent.value = ''
    ElMessage.success('问题已发送给导师')
  } finally {
    questionSubmitting.value = false
  }
}

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchProfile().catch(() => null)
  }
  await loadRelations()
  if (isTeacherView.value && route.query.studentId) {
    await router.replace(`/mentor/students/${route.query.studentId}`)
    return
  }
  if (isStudentView.value && !hasMentor.value) {
    await searchTeachers()
  }
  if (isTeacherView.value && route.query.view === 'applications') {
    document.querySelector('#mentor-applications')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
})
</script>

<style scoped>
.mentor-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
}

.mentor-info,
.teacher-info,
.application-main {
  flex: 1;
  min-width: 0;
}

.mentor-name,
.teacher-name,
.application-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow-wrap: anywhere;
}

.mentor-email,
.teacher-email,
.application-meta {
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
  overflow-wrap: anywhere;
}

.mentor-status {
  margin-top: 8px;
}

.teacher-list {
  margin-top: 16px;
}

.teacher-item,
.application-item,
.pending-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}

.teacher-item:last-child,
.application-item:last-child,
.pending-item:last-child {
  border-bottom: none;
}

.clickable-student {
  cursor: pointer;
  border-radius: 8px;
  padding-left: 8px;
  padding-right: 8px;
  transition: background-color 0.2s ease;
}

.clickable-student:hover,
.clickable-student:focus-visible {
  background: #f5f9ff;
  outline: none;
}

.application-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.pending-card {
  margin-top: 20px;
}

.confirmed-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #606266;
  font-size: 14px;
  padding: 18px 0;
}

.question-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.empty-tip {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
}

@media (max-width: 768px) {
  .application-item,
  .pending-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .application-actions {
    width: 100%;
  }
}
</style>

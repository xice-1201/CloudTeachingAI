<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">导师关系</span>
    </div>

    <template v-if="userStore.isTeacher">
      <el-row :gutter="20">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" header="待处理申请" v-loading="relationLoading">
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
            <div v-for="student in students" :key="student.id" class="teacher-item">
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
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无导师" :image-size="80" />
          </el-card>

          <el-card v-if="applications.length > 0" shadow="never" header="申请进度" class="pending-card">
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
          <el-card shadow="never" header="申请导师">
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
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { MentorApplication, User } from '@/types'

const userStore = useUserStore()
const mentor = ref<User | null>(null)
const students = ref<User[]>([])
const applications = ref<MentorApplication[]>([])
const teachers = ref<User[]>([])
const keyword = ref('')
const loading = ref(false)
const relationLoading = ref(false)
const applyingMentorId = ref<number | null>(null)
const handlingApplicationId = ref<number | null>(null)

const pendingMentorIds = computed(() => new Set(applications.value.map((item) => item.mentorId)))

function normalizeMentor(value?: User | null) {
  return value && typeof value.id === 'number' ? value : null
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
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
  } finally {
    relationLoading.value = false
  }
}

async function applyMentor(mentorId: number) {
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

onMounted(async () => {
  await loadRelations()
  if (userStore.isStudent && !mentor.value) {
    await searchTeachers()
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

.application-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.pending-card {
  margin-top: 20px;
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

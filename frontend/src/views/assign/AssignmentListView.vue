<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">作业管理</span>
      <el-button v-if="!userStore.isStudent" type="primary" @click="openCreateDialog">创建作业</el-button>
    </div>

    <el-tabs v-if="userStore.isStudent" v-model="activeTab">
      <el-tab-pane label="待完成" name="pending" />
      <el-tab-pane label="已提交" name="submitted" />
    </el-tabs>

    <div v-loading="loading">
      <el-table :data="displayAssignments" style="width: 100%; margin-top: 16px">
        <el-table-column prop="title" label="作业标题" min-width="220" />
        <el-table-column prop="courseTitle" label="所属课程" min-width="180" />
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row.dueDate) }">{{ formatDate(row.dueDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxScore" label="满分" width="90" />
        <el-table-column v-if="userStore.isStudent" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(studentSubmissionMap[row.id]?.status)">
              {{ statusLabel(studentSubmissionMap[row.id]?.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/assignments/${row.id}`)">查看</el-button>
            <el-button
              v-if="!userStore.isStudent"
              type="primary"
              link
              @click="$router.push(`/assignments/${row.id}/submissions`)"
            >
              批改
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && displayAssignments.length === 0" description="暂无作业" />
    </div>

    <el-dialog v-model="createVisible" title="创建作业" width="640px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="createForm.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option
              v-for="course in teacherCourses"
              :key="course.id"
              :label="course.title"
              :value="String(course.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="作业标题" prop="title">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="作业说明" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="评分标准" prop="gradingCriteria">
          <el-input v-model="createForm.gradingCriteria" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="满分" prop="maxScore">
          <el-input-number v-model="createForm.maxScore" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="截止时间" prop="dueDate">
          <el-date-picker
            v-model="createForm.dueDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { assignApi } from '@/api/assign'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Assignment, Course, Submission } from '@/types'

const userStore = useUserStore()

const loading = ref(false)
const creating = ref(false)
const createVisible = ref(false)
const activeTab = ref('pending')
const assignments = ref<Assignment[]>([])
const teacherCourses = ref<Course[]>([])
const studentSubmissionMap = ref<Record<number, Submission | null>>({})
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  courseId: '',
  title: '',
  description: '',
  gradingCriteria: '',
  maxScore: 100,
  dueDate: '',
})

const createRules: FormRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  title: [{ required: true, message: '请输入作业标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入作业说明', trigger: 'blur' }],
  dueDate: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
}

const displayAssignments = computed(() => {
  if (!userStore.isStudent) {
    return assignments.value
  }

  return assignments.value.filter((assignment) => {
    const submission = studentSubmissionMap.value[assignment.id]
    if (activeTab.value === 'submitted') {
      return Boolean(submission)
    }
    return !submission
  })
})

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function isOverdue(value: string) {
  return new Date(value).getTime() < Date.now()
}

function statusTagType(status?: string | null) {
  return status ? ({
    SUBMITTED: 'warning',
    AI_GRADING: 'warning',
    AI_GRADED: 'info',
    GRADING_FAILED: 'danger',
    PENDING_MANUAL: 'warning',
    REVIEWED: 'success',
  }[status] ?? 'info') : 'info'
}

function statusLabel(status?: string | null) {
  return status ? ({
    SUBMITTED: '已提交',
    AI_GRADING: 'AI 批改中',
    AI_GRADED: 'AI 已批改',
    GRADING_FAILED: '批改失败',
    PENDING_MANUAL: '待人工批改',
    REVIEWED: '已复核',
  }[status] ?? status) : '待完成'
}

function resetCreateForm() {
  createForm.courseId = ''
  createForm.title = ''
  createForm.description = ''
  createForm.gradingCriteria = ''
  createForm.maxScore = 100
  createForm.dueDate = ''
}

function openCreateDialog() {
  resetCreateForm()
  createVisible.value = true
}

async function fetchTeacherAssignments() {
  const coursesResponse = await courseApi.listCourses({ page: 1, pageSize: 100 })
  teacherCourses.value = coursesResponse.items

  const allAssignments: Assignment[] = []
  await Promise.all(
    coursesResponse.items.map(async (course) => {
      const response = await assignApi.listAssignments(String(course.id), { page: 1, pageSize: 100 })
      allAssignments.push(...response.items)
    }),
  )

  assignments.value = allAssignments.sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime())
}

async function fetchStudentAssignments() {
  const coursesResponse = await courseApi.listEnrolledCourses({ page: 1, pageSize: 100 })
  const allAssignments: Assignment[] = []
  const submissionEntries: Array<[number, Submission | null]> = []

  await Promise.all(
    coursesResponse.items.map(async (course) => {
      const response = await assignApi.listAssignments(String(course.id), { page: 1, pageSize: 100 })
      allAssignments.push(...response.items)

      await Promise.all(
        response.items.map(async (assignment) => {
          const submission = await assignApi.getMySubmission(String(assignment.id)).catch(() => null)
          submissionEntries.push([assignment.id, submission])
        }),
      )
    }),
  )

  assignments.value = allAssignments.sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime())
  studentSubmissionMap.value = Object.fromEntries(submissionEntries)
}

async function fetchData() {
  loading.value = true
  try {
    if (userStore.isStudent) {
      await fetchStudentAssignments()
    } else {
      await fetchTeacherAssignments()
    }
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  await createFormRef.value?.validate()
  creating.value = true
  try {
    await assignApi.createAssignment(createForm.courseId, {
      title: createForm.title,
      description: createForm.description,
      gradingCriteria: createForm.gradingCriteria || undefined,
      maxScore: createForm.maxScore,
      dueDate: createForm.dueDate,
      submitType: 'TEXT',
    })
    createVisible.value = false
    ElMessage.success('作业已创建')
    await fetchData()
  } finally {
    creating.value = false
  }
}

watch(activeTab, () => {
  if (userStore.isStudent) {
    assignments.value = [...assignments.value]
  }
})

onMounted(fetchData)
</script>

<style scoped>
.overdue {
  color: #f56c6c;
}
</style>

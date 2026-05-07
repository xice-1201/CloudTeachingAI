<template>
  <div class="page-container" v-loading="loading">
    <template v-if="assignment">
      <div class="page-header">
        <span class="page-title">{{ assignment.title }}</span>
        <el-tag :type="isOverdue ? 'danger' : 'success'">
          截止：{{ formatDate(assignment.dueDate) }}
        </el-tag>
      </div>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-card shadow="never" header="作业说明" style="margin-bottom: 20px">
            <p class="description">{{ assignment.description }}</p>
            <div v-if="assignment.gradingCriteria" class="criteria">
              <div class="section-label">评分标准</div>
              <p class="description">{{ assignment.gradingCriteria }}</p>
            </div>
          </el-card>

          <el-card v-if="userStore.isStudent" shadow="never" :header="submission ? '我的提交' : '提交作业'">
            <template v-if="submission">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="提交时间">{{ formatDate(submission.submittedAt) }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="submissionTagType(submission.status)">{{ submissionLabel(submission.status) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item v-if="submission.score !== undefined" :label="submission.status === 'REVIEWED' ? '最终得分' : 'AI 建议分'">
                  {{ submission.score }} / {{ assignment.maxScore }}
                </el-descriptions-item>
              </el-descriptions>
              <div class="section-block">
                <div class="section-label">提交内容</div>
                <p class="description">{{ submission.content }}</p>
              </div>
              <div v-if="submission.aiFeedback" class="section-block">
                <div class="section-label">AI 批改建议</div>
                <p class="description">{{ submission.aiFeedback }}</p>
              </div>
              <div v-if="submission.finalFeedback" class="section-block">
                <div class="section-label">教师最终反馈</div>
                <p class="description">{{ submission.finalFeedback }}</p>
              </div>
              <div v-else-if="submission.feedback && !submission.aiFeedback" class="section-block">
                <div class="section-label">反馈</div>
                <p class="description">{{ submission.feedback }}</p>
              </div>
            </template>

            <template v-else>
              <el-alert
                v-if="isOverdue"
                type="warning"
                :closable="false"
                title="当前作业已过截止时间，不能再提交"
                style="margin-bottom: 16px"
              />
              <el-form ref="formRef" :model="form" :rules="rules">
                <el-form-item prop="content">
                  <el-input
                    v-model="form.content"
                    type="textarea"
                    :rows="10"
                    placeholder="请输入作业内容..."
                    :disabled="isOverdue"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="submitting" :disabled="isOverdue" @click="handleSubmit">
                    提交作业
                  </el-button>
                </el-form-item>
              </el-form>
            </template>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" header="作业信息">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="所属课程">{{ assignment.courseTitle || '-' }}</el-descriptions-item>
              <el-descriptions-item label="提交方式">{{ submitTypeLabel(assignment.submitType) }}</el-descriptions-item>
              <el-descriptions-item label="满分">{{ assignment.maxScore }} 分</el-descriptions-item>
              <el-descriptions-item label="截止时间">{{ formatDate(assignment.dueDate) }}</el-descriptions-item>
              <el-descriptions-item label="发布时间">{{ formatDate(assignment.createdAt) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { assignApi } from '@/api/assign'
import { useUserStore } from '@/store/user'
import type { Assignment, Submission } from '@/types'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const assignment = ref<Assignment | null>(null)
const submission = ref<Submission | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ content: '' })

const isOverdue = computed(() => assignment.value ? new Date(assignment.value.dueDate).getTime() < Date.now() : false)

const rules: FormRules = {
  content: [{ required: true, message: '请输入作业内容', trigger: 'blur' }],
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function submitTypeLabel(type?: string) {
  return {
    TEXT: '文本',
    FILE: '文件',
    BOTH: '文本 + 文件',
  }[type ?? 'TEXT'] ?? type ?? '-'
}

function submissionTagType(status: string) {
  return {
    SUBMITTED: 'warning',
    AI_GRADING: 'warning',
    AI_GRADED: 'info',
    GRADING_FAILED: 'danger',
    PENDING_MANUAL: 'warning',
    REVIEWED: 'success',
  }[status] ?? 'info'
}

function submissionLabel(status: string) {
  return {
    SUBMITTED: '已提交',
    AI_GRADING: 'AI 批改中',
    AI_GRADED: 'AI 已批改',
    GRADING_FAILED: '批改失败',
    PENDING_MANUAL: '待人工批改',
    REVIEWED: '已复核',
  }[status] ?? status
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    submission.value = await assignApi.submitAssignment(String(assignment.value!.id), { content: form.content })
    ElMessage.success('作业已提交')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const assignmentId = String(route.params.id)
    assignment.value = await assignApi.getAssignment(assignmentId)
    if (userStore.isStudent) {
      submission.value = await assignApi.getMySubmission(assignmentId).catch(() => null)
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.description {
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
}

.criteria,
.section-block {
  margin-top: 16px;
}

.section-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
</style>

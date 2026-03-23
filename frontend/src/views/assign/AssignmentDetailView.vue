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
            <p style="line-height: 1.8; color: #606266">{{ assignment.description }}</p>
          </el-card>

          <!-- 学生提交区 -->
          <el-card v-if="userStore.isStudent" shadow="never" :header="submission ? '我的提交' : '提交作业'">
            <template v-if="submission">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="提交时间">{{ formatDate(submission.submittedAt) }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="submissionTagType(submission.status)">{{ submissionLabel(submission.status) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item v-if="submission.score !== undefined" label="得分">
                  {{ submission.score }} / {{ assignment.maxScore }}
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="submission.feedback" style="margin-top: 16px">
                <div style="font-size: 13px; color: #909399; margin-bottom: 8px">教师反馈</div>
                <p style="color: #606266; line-height: 1.7">{{ submission.feedback }}</p>
              </div>
            </template>
            <template v-else>
              <el-form ref="formRef" :model="form" :rules="rules">
                <el-form-item prop="content">
                  <el-input v-model="form.content" type="textarea" :rows="8" placeholder="请输入作业内容..." />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="submitting" @click="handleSubmit">提交作业</el-button>
                </el-form-item>
              </el-form>
            </template>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" header="作业信息">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="满分">{{ assignment.maxScore }} 分</el-descriptions-item>
              <el-descriptions-item label="截止时间">{{ formatDate(assignment.dueDate) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
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

const isOverdue = computed(() => assignment.value ? new Date(assignment.value.dueDate) < new Date() : false)

const rules: FormRules = {
  content: [{ required: true, message: '请输入作业内容', trigger: 'blur' }],
}

function formatDate(d: string) { return new Date(d).toLocaleString('zh-CN') }
function submissionTagType(s: string) { return { PENDING: 'warning', GRADED: 'info', REVIEWED: 'success' }[s] ?? 'info' }
function submissionLabel(s: string) { return { PENDING: '待批改', GRADED: 'AI已批改', REVIEWED: '已复核' }[s] ?? s }

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    submission.value = await assignApi.submitAssignment(assignment.value!.id, { content: form.content })
    ElMessage.success('作业已提交')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id as string
    assignment.value = await assignApi.getAssignment(id)
    if (userStore.isStudent) {
      submission.value = await assignApi.getMySubmission(id).catch(() => null)
    }
  } finally {
    loading.value = false
  }
})
</script>

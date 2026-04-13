<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <span class="page-title">提交列表</span>
    </div>

    <el-table :data="submissions" style="width: 100%">
      <el-table-column prop="studentId" label="学生 ID" width="120" />
      <el-table-column label="提交内容" min-width="260">
        <template #default="{ row }">
          <div class="content-preview">{{ row.content }}</div>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">{{ formatDate(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="得分" width="100" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="openReview(row)">复核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="reviewVisible" title="复核作业" width="640px">
      <template v-if="reviewing">
        <div class="section-label">学生答案</div>
        <div class="answer-box">{{ reviewing.content }}</div>

        <el-form :model="reviewForm" label-width="80px" style="margin-top: 20px">
          <el-form-item label="得分">
            <el-input-number v-model="reviewForm.score" :min="0" :max="maxScore" />
            <span style="margin-left: 8px; color: #909399">/ {{ maxScore }}</span>
          </el-form-item>
          <el-form-item label="反馈">
            <el-input v-model="reviewForm.feedback" type="textarea" :rows="5" placeholder="输入教师反馈..." />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleReview">确认复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assignApi } from '@/api/assign'
import type { Submission } from '@/types'

const route = useRoute()

const loading = ref(false)
const submitting = ref(false)
const submissions = ref<Submission[]>([])
const reviewing = ref<Submission | null>(null)
const reviewVisible = ref(false)
const maxScore = ref(100)
const reviewForm = reactive({
  score: 0,
  feedback: '',
})

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function tagType(status: string) {
  return {
    SUBMITTED: 'warning',
    AI_GRADING: 'warning',
    AI_GRADED: 'info',
    GRADING_FAILED: 'danger',
    PENDING_MANUAL: 'warning',
    REVIEWED: 'success',
  }[status] ?? 'info'
}

function statusLabel(status: string) {
  return {
    SUBMITTED: '已提交',
    AI_GRADING: 'AI 批改中',
    AI_GRADED: 'AI 已批改',
    GRADING_FAILED: '批改失败',
    PENDING_MANUAL: '待人工批改',
    REVIEWED: '已复核',
  }[status] ?? status
}

function openReview(submission: Submission) {
  reviewing.value = submission
  reviewForm.score = submission.score ?? 0
  reviewForm.feedback = submission.feedback ?? ''
  reviewVisible.value = true
}

async function handleReview() {
  submitting.value = true
  try {
    const reviewed = await assignApi.reviewSubmission(String(reviewing.value!.id), reviewForm)
    const index = submissions.value.findIndex((submission) => submission.id === reviewed.id)
    if (index !== -1) {
      submissions.value[index] = reviewed
    }
    reviewVisible.value = false
    ElMessage.success('复核完成')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const assignmentId = String(route.params.id)
    const assignment = await assignApi.getAssignment(assignmentId)
    maxScore.value = assignment.maxScore
    const response = await assignApi.listSubmissions(assignmentId, { page: 1, pageSize: 100 })
    submissions.value = response.items
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.content-preview,
.answer-box {
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}

.answer-box {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
}

.section-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
</style>

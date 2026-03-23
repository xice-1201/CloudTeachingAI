<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <span class="page-title">提交列表</span>
    </div>

    <el-table :data="submissions" style="width: 100%">
      <el-table-column prop="studentId" label="学生 ID" width="160" />
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

    <el-dialog v-model="reviewVisible" title="复核作业" width="600px">
      <template v-if="reviewing">
        <div style="margin-bottom: 16px">
          <div style="font-size: 13px; color: #909399; margin-bottom: 8px">学生答案</div>
          <p style="color: #303133; line-height: 1.7; background: #f5f7fa; padding: 12px; border-radius: 4px">
            {{ reviewing.content }}
          </p>
        </div>
        <el-form :model="reviewForm" label-width="80px">
          <el-form-item label="得分">
            <el-input-number v-model="reviewForm.score" :min="0" :max="maxScore" />
            <span style="margin-left: 8px; color: #909399">/ {{ maxScore }}</span>
          </el-form-item>
          <el-form-item label="反馈">
            <el-input v-model="reviewForm.feedback" type="textarea" :rows="4" placeholder="输入反馈意见..." />
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
import { ref, reactive, onMounted } from 'vue'
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
const reviewForm = reactive({ score: 0, feedback: '' })

function formatDate(d: string) { return new Date(d).toLocaleString('zh-CN') }
function tagType(s: string) { return { PENDING: 'warning', GRADED: 'info', REVIEWED: 'success' }[s] ?? 'info' }
function statusLabel(s: string) { return { PENDING: '待批改', GRADED: 'AI已批改', REVIEWED: '已复核' }[s] ?? s }

function openReview(sub: Submission) {
  reviewing.value = sub
  reviewForm.score = sub.score ?? 0
  reviewForm.feedback = sub.feedback ?? ''
  reviewVisible.value = true
}

async function handleReview() {
  submitting.value = true
  try {
    await assignApi.reviewSubmission(reviewing.value!.id, reviewForm)
    ElMessage.success('复核完成')
    reviewVisible.value = false
    const idx = submissions.value.findIndex((s) => s.id === reviewing.value!.id)
    if (idx !== -1) {
      submissions.value[idx] = { ...submissions.value[idx], ...reviewForm, status: 'REVIEWED' }
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id as string
    const assignment = await assignApi.getAssignment(id)
    maxScore.value = assignment.maxScore
    const res = await assignApi.listSubmissions(id, { page: 1, pageSize: 100 })
    submissions.value = res.items
  } finally {
    loading.value = false
  }
})
</script>

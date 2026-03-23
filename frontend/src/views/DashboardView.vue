<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">首页</span>
    </div>

    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
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
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="待完成作业">
          <div v-if="pendingAssignments.length === 0" class="empty-tip">暂无待完成作业</div>
          <div v-for="a in pendingAssignments" :key="a.id" class="assignment-item" @click="$router.push(`/assignments/${a.id}`)">
            <div class="assignment-title">{{ a.title }}</div>
            <div class="assignment-due">截止：{{ formatDate(a.dueDate) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Reading, EditPen, TrendCharts, ChatDotRound } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import type { Assignment } from '@/types'

const recentCourses = ref<any[]>([])
const pendingAssignments = ref<Assignment[]>([])

const stats = ref([
  { label: '已选课程', value: 0, icon: Reading, color: '#409eff' },
  { label: '学习进度', value: '0%', icon: TrendCharts, color: '#67c23a' },
  { label: '待完成作业', value: 0, icon: EditPen, color: '#e6a23c' },
  { label: 'AI 对话次数', value: 0, icon: ChatDotRound, color: '#909399' },
])

function formatDate(date: string) {
  return new Date(date).toLocaleDateString('zh-CN')
}

onMounted(async () => {
  const [coursesRes] = await Promise.allSettled([
    courseApi.listEnrolledCourses({ page: 1, pageSize: 5 }),
  ])
  if (coursesRes.status === 'fulfilled') {
    recentCourses.value = coursesRes.value.items.map((c) => ({ ...c, progress: Math.floor(Math.random() * 100) }))
    stats.value[0].value = coursesRes.value.total
  }
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
.assignment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.assignment-item:last-child {
  border-bottom: none;
}
.assignment-title {
  font-size: 14px;
  color: #303133;
}
.assignment-due {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}
.empty-tip {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
}
</style>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <span class="page-title">个性化学习路线</span>
      <el-button type="primary" :loading="generating" @click="handleGenerate">
        重新生成路线
      </el-button>
    </div>

    <el-card v-if="path" shadow="never" class="overview-card">
      <template #header>
        <div class="overview-header">
          <div>
            <div class="overview-title">本轮推荐重点</div>
            <div class="overview-subtitle">系统会优先围绕你当前掌握度较弱的知识点推荐可学习资源。</div>
          </div>
          <div class="overview-time">生成时间：{{ formatTime(path.generatedAt) }}</div>
        </div>
      </template>

      <div class="focus-points">
        <div v-for="item in path.focusKnowledgePoints" :key="item.knowledgePointId" class="focus-item">
          <div class="focus-name">{{ item.knowledgePointName }}</div>
          <div class="focus-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
          <el-progress
            :percentage="Math.round(item.masteryLevel * 100)"
            :color="masteryColor(item.masteryLevel)"
            :stroke-width="8"
          />
        </div>
      </div>
    </el-card>

    <el-empty
      v-if="!loading && !path"
      description="暂时还没有可生成的学习路线，先完成能力测试或开始学习带知识点标签的课程资源吧"
    >
      <template #extra>
        <div class="empty-actions">
          <el-button type="primary" @click="$router.push('/learning/ability-test')">去做能力测试</el-button>
          <el-button @click="$router.push('/courses')">浏览课程</el-button>
        </div>
      </template>
    </el-empty>

    <div v-else-if="path" class="path-container">
      <div v-for="item in path.resources" :key="item.resourceId" class="path-step">
        <div class="step-index">{{ item.orderIndex }}</div>
        <div class="step-card" @click="openResource(item)">
          <div class="step-top">
            <div>
              <div class="step-title">{{ item.resourceTitle }}</div>
              <div class="step-course">{{ item.courseTitle }}<span v-if="item.chapterTitle"> · {{ item.chapterTitle }}</span></div>
            </div>
            <el-tag size="small" effect="plain">{{ item.focusKnowledgePointName }}</el-tag>
          </div>
          <div class="step-reason">{{ item.reason }}</div>
          <div class="step-progress">
            <span>当前学习进度</span>
            <el-progress
              :percentage="Math.round(item.currentProgress * 100)"
              :stroke-width="8"
              :color="masteryColor(item.currentProgress)"
            />
          </div>
        </div>
        <div v-if="item.orderIndex < path.resources.length" class="step-connector" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { learnApi } from '@/api/learn'
import type { LearningPath, PathResource } from '@/types'

const router = useRouter()
const loading = ref(false)
const generating = ref(false)
const path = ref<LearningPath | null>(null)

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function formatTime(value: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

function openResource(item: PathResource) {
  router.push(`/courses/${item.courseId}/learn/${item.resourceId}`)
}

async function loadPath() {
  loading.value = true
  try {
    path.value = await learnApi.getLearningPath().catch(() => null)
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  generating.value = true
  try {
    path.value = await learnApi.generateLearningPath()
    if (path.value) {
      ElMessage.success('学习路线已更新')
    } else {
      ElMessage.warning('当前还没有足够的数据生成学习路线')
    }
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadPath()
})
</script>

<style scoped>
.overview-card {
  margin-bottom: 20px;
}

.overview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.overview-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.overview-subtitle,
.overview-time {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.focus-points {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.focus-item {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fff;
}

.focus-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.focus-path {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  gap: 12px;
}

.path-container {
  max-width: 860px;
}

.path-step {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding-left: 56px;
}

.step-index {
  position: absolute;
  left: 0;
  top: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: #409eff;
  color: #fff;
  font-weight: 700;
  z-index: 1;
}

.step-card {
  width: 100%;
  padding: 18px 20px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}

.step-card:hover {
  border-color: #409eff;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}

.step-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.step-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.step-course {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.step-reason {
  margin-top: 14px;
  color: #606266;
  line-height: 1.7;
  font-size: 14px;
}

.step-progress {
  margin-top: 16px;
  display: grid;
  gap: 8px;
  color: #606266;
  font-size: 13px;
}

.step-connector {
  width: 2px;
  height: 28px;
  margin-left: 18px;
  background: #dcdfe6;
}

@media (max-width: 768px) {
  .overview-header,
  .step-top,
  .empty-actions {
    flex-direction: column;
  }
}
</style>

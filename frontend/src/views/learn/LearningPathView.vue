<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <span class="page-title">个性化学习路线</span>
      <el-button type="primary" :loading="generating" @click="handleGenerate">
        重新生成路线
      </el-button>
    </div>

    <section v-if="hasPath" class="route-summary">
      <div class="summary-main">
        <div class="summary-title">本轮推荐重点</div>
        <div class="summary-subtitle">
          根据能力图谱、学习进度和已选课程资源，优先推荐当前最值得补强的知识点。
        </div>
      </div>
      <div class="summary-time">生成时间：{{ formatTime(path?.generatedAt) }}</div>
    </section>

    <div v-if="hasPath" class="focus-points">
      <div v-for="item in path?.focusKnowledgePoints" :key="item.knowledgePointId" class="focus-item">
        <div class="focus-name">{{ item.knowledgePointName }}</div>
        <div class="focus-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
        <el-progress
          :percentage="Math.round(item.masteryLevel * 100)"
          :color="masteryColor(item.masteryLevel)"
          :stroke-width="8"
        />
      </div>
    </div>

    <el-empty
      v-if="showEmpty"
      description="暂时还没有可用的学习路线。完成能力测试，或先学习带知识点标签的课程资源后再生成。"
    >
      <template #extra>
        <div class="empty-actions">
          <el-button type="primary" @click="router.push('/learning/ability-test')">去做能力测试</el-button>
          <el-button @click="router.push('/courses')">浏览课程</el-button>
        </div>
      </template>
    </el-empty>

    <el-empty
      v-else-if="showNoResources"
      description="已经识别到推荐重点，但当前课程中还没有匹配的可学习资源。"
    >
      <template #extra>
        <el-button type="primary" :loading="generating" @click="handleGenerate">再次生成</el-button>
      </template>
    </el-empty>

    <div v-else-if="hasResources" class="path-container">
      <div v-for="item in path?.resources" :key="item.resourceId" class="path-step">
        <div class="step-index">{{ item.orderIndex }}</div>
        <button type="button" class="step-card" @click="openResource(item)">
          <div class="step-top">
            <div class="step-heading">
              <div class="step-title">{{ item.resourceTitle }}</div>
              <div class="step-course">
                {{ item.courseTitle }}
                <span v-if="item.chapterTitle"> / {{ item.chapterTitle }}</span>
              </div>
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
        </button>
        <div v-if="item.orderIndex < (path?.resources.length ?? 0)" class="step-connector" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { learnApi } from '@/api/learn'
import type { LearningPath, PathResource } from '@/types'

const router = useRouter()
const loading = ref(false)
const generating = ref(false)
const path = ref<LearningPath | null>(null)

const hasPath = computed(() => Boolean(path.value))
const hasResources = computed(() => Boolean(path.value?.resources?.length))
const showEmpty = computed(() => !loading.value && !path.value)
const showNoResources = computed(() => !loading.value && hasPath.value && !hasResources.value)

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function formatTime(value?: string) {
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
    if (path.value?.resources?.length) {
      ElMessage.success('学习路线已更新')
    } else if (path.value) {
      ElMessage.warning('已识别推荐重点，但还没有匹配资源')
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
.route-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.summary-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.summary-subtitle,
.summary-time {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.summary-time {
  flex-shrink: 0;
}

.focus-points {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 22px;
}

.focus-item {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
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
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;
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

.step-heading {
  min-width: 0;
}

.step-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow-wrap: anywhere;
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
  margin-left: -38px;
  background: #dcdfe6;
}

@media (max-width: 768px) {
  .route-summary {
    flex-direction: column;
  }

  .summary-time {
    flex-shrink: 1;
  }

  .empty-actions,
  .step-top {
    flex-direction: column;
  }

  .path-step {
    padding-left: 44px;
  }

  .step-index {
    width: 32px;
    height: 32px;
  }
}
</style>

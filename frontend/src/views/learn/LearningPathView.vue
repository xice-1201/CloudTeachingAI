<template>
  <div :class="embedded ? 'learning-path-panel' : 'page-container'" v-loading="loading">
    <div v-if="!embedded" class="page-header">
      <span class="page-title">个性化学习路线</span>
      <el-button v-if="hasAbilityData" type="primary" :loading="generating" @click="handleGenerate">
        重新生成路线
      </el-button>
      <el-button v-else type="primary" @click="router.push('/learning/ability-test')">去做能力测试</el-button>
    </div>
    <div v-else class="embedded-header">
      <div>
        <div class="embedded-title">个性化学习路线</div>
        <div class="embedded-subtitle">根据能力图谱、学习进度和课程资源推荐下一步</div>
      </div>
      <el-button v-if="hasAbilityData" type="primary" :loading="generating" @click="handleGenerate">
        重新生成路线
      </el-button>
      <el-button v-else type="primary" @click="router.push('/learning/ability-test')">去做能力测试</el-button>
    </div>

    <el-empty
      v-if="showNoAbilityData"
      description="还没有个人能力画像，暂不生成推荐学习路线。请先完成能力测试。"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/learning/ability-test')">前往能力测试</el-button>
      </template>
    </el-empty>

    <section v-if="hasAbilityData && hasPath" class="route-summary">
      <div class="summary-main">
        <div class="summary-title">本轮推荐重点</div>
        <div class="summary-subtitle">
          根据能力图谱、学习进度和已选课程资源，优先推荐当前最值得补强的知识点。
        </div>
      </div>
      <div class="summary-time">生成时间：{{ formatTime(path?.generatedAt) }}</div>
    </section>

    <div v-if="hasAbilityData && hasPath" class="focus-points">
      <div v-for="item in path?.focusKnowledgePoints" :key="item.knowledgePointId" class="focus-item">
        <div class="focus-head">
          <div class="focus-name">{{ item.knowledgePointName }}</div>
          <el-button link type="primary" :icon="ChatDotRound" @click="askAiForFocus(item)">问 AI</el-button>
        </div>
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

    <div v-else-if="hasResources" class="route-board">
      <div class="route-start">
        <span class="route-start-icon">
          <el-icon><Flag /></el-icon>
        </span>
        <span>从当前掌握情况出发</span>
      </div>
      <div class="route-scroll">
        <div
          v-for="(item, index) in path?.resources"
          :key="item.resourceId"
          class="route-node"
          :class="routeNodeClass(item)"
        >
          <div class="route-track">
            <div class="route-dot">
              <el-icon v-if="isCompleted(item)"><Finished /></el-icon>
              <span v-else>{{ item.orderIndex }}</span>
            </div>
            <div v-if="index < (path?.resources.length ?? 0) - 1" class="route-connector">
              <span class="route-connector-fill" :style="{ width: connectorProgress(item) }" />
            </div>
          </div>

          <button type="button" class="milestone-card" @click="openResource(item)">
            <div class="milestone-kicker">
              <span>第 {{ item.orderIndex }} 站</span>
              <el-tag size="small" :type="pathStatusTagType(item)" effect="light">
                {{ item.statusLabel || pathStatusLabel(item) }}
              </el-tag>
            </div>
            <div class="milestone-title">{{ item.resourceTitle }}</div>
            <div class="milestone-course">
              {{ item.courseTitle }}
              <span v-if="item.chapterTitle"> / {{ item.chapterTitle }}</span>
            </div>
            <div class="milestone-focus">
              <span>{{ item.focusKnowledgePointName }}</span>
              <span v-if="item.recommendationScore != null">推荐度 {{ Math.round(item.recommendationScore * 100) }}%</span>
            </div>
            <div class="milestone-reason">{{ item.reason }}</div>
            <div class="milestone-progress">
              <span>进度 {{ Math.round(item.currentProgress * 100) }}%</span>
              <el-progress
                :percentage="Math.round(item.currentProgress * 100)"
                :stroke-width="8"
                :color="masteryColor(item.currentProgress)"
              />
            </div>
            <div class="milestone-footer">
              <span>{{ nextStepHint(item) }}</span>
              <span class="milestone-action">{{ item.actionLabel || pathActionLabel(item) }}</span>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Finished, Flag } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import type { LearningPath, PathResource } from '@/types'

const props = withDefaults(defineProps<{
  embedded?: boolean
}>(), {
  embedded: false,
})

const router = useRouter()
const loading = ref(false)
const generating = ref(false)
const path = ref<LearningPath | null>(null)
const hasAbilityData = ref(false)
const enrolledCourseIds = ref<number[]>([])
const embedded = computed(() => props.embedded)
const returnUrl = computed(() => (embedded.value ? '/dashboard' : '/learning/path'))
const returnLabel = computed(() => (embedded.value ? '返回首页' : '返回路线'))

const hasPath = computed(() => Boolean(path.value))
const hasResources = computed(() => Boolean(path.value?.resources?.length))
const showNoAbilityData = computed(() => !loading.value && !hasAbilityData.value)
const showEmpty = computed(() => !loading.value && hasAbilityData.value && !path.value)
const showNoResources = computed(() => !loading.value && hasAbilityData.value && hasPath.value && !hasResources.value)

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
  if (!enrolledCourseIds.value.includes(item.courseId)) {
    ElMessage.info('该资源来自未选课程，请先完成选课后再学习对应资源')
    router.push({
      path: `/courses/${item.courseId}`,
      query: {
        fromPath: '1',
        requireEnroll: '1',
        recommendedResourceId: item.resourceId,
        returnUrl: returnUrl.value,
      },
    })
    return
  }

  router.push({
    path: `/courses/${item.courseId}/learn/${item.resourceId}`,
    query: {
      fromPath: '1',
      returnUrl: returnUrl.value,
    },
  })
}

function pathStatusLabel(item: PathResource) {
  if (item.learningStatus === 'COMPLETED' || item.currentProgress >= 0.999) return '已完成'
  if (item.learningStatus === 'IN_PROGRESS' || item.currentProgress > 0) return '学习中'
  return '未开始'
}

function isCompleted(item: PathResource) {
  return item.learningStatus === 'COMPLETED' || item.currentProgress >= 0.999
}

function routeNodeClass(item: PathResource) {
  if (isCompleted(item)) return 'is-completed'
  if (item.learningStatus === 'IN_PROGRESS' || item.currentProgress > 0) return 'is-current'
  return 'is-pending'
}

function connectorProgress(item: PathResource) {
  if (isCompleted(item)) return '100%'
  if (item.learningStatus === 'IN_PROGRESS' || item.currentProgress > 0) return `${Math.max(26, Math.round(item.currentProgress * 100))}%`
  return '0%'
}

function pathActionLabel(item: PathResource) {
  if (item.learningStatus === 'COMPLETED' || item.currentProgress >= 0.999) return '复习资源'
  if (item.learningStatus === 'IN_PROGRESS' || item.currentProgress > 0) return '继续学习'
  return '开始学习'
}

function pathStatusTagType(item: PathResource) {
  if (item.learningStatus === 'COMPLETED' || item.currentProgress >= 0.999) return 'success' as const
  if (item.learningStatus === 'IN_PROGRESS' || item.currentProgress > 0) return 'warning' as const
  return 'info' as const
}

function nextStepHint(item: PathResource) {
  if (item.currentProgress > 0) {
    return `已完成 ${Math.round(item.currentProgress * 100)}%，建议从上次位置继续。`
  }
  return '建议现在开始，完成后路线会根据学习进度继续调整。'
}

function askAiForFocus(item: LearningPath['focusKnowledgePoints'][number]) {
  router.push({
    name: 'Chat',
    query: {
      knowledgePointId: item.knowledgePointId,
      knowledgePointName: item.knowledgePointName,
      returnUrl: returnUrl.value,
      returnLabel: returnLabel.value,
    },
  })
}

async function loadPath() {
  loading.value = true
  try {
    const abilityMap = await learnApi.getAbilityMap().catch(() => [])
    hasAbilityData.value = abilityMap.length > 0
    if (!hasAbilityData.value) {
      path.value = null
      return
    }
    enrolledCourseIds.value = await loadEnrolledCourseIds()
    path.value = await learnApi.getLearningPath().catch(() => null)
  } finally {
    loading.value = false
  }
}

async function loadEnrolledCourseIds() {
  const response = await courseApi.listEnrolledCourses(
    { page: 1, pageSize: 200 },
    { headers: { 'X-Silent-Error': 'true' } },
  ).catch(() => null)
  return response?.items?.map((course) => course.id) ?? []
}

async function handleGenerate() {
  if (!hasAbilityData.value) {
    path.value = null
    ElMessage.warning('请先完成能力测试，形成个人能力画像后再生成学习路线')
    router.push('/learning/ability-test')
    return
  }
  generating.value = true
  try {
    enrolledCourseIds.value = await loadEnrolledCourseIds()
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
.learning-path-panel {
  margin-top: 20px;
}

.embedded-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.embedded-title {
  color: #303133;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.embedded-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

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

.focus-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
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

.route-board {
  padding: 18px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #f8fafc;
}

.route-start {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.route-start-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: #ecf5ff;
  color: #409eff;
}

.route-scroll {
  display: flex;
  gap: 0;
  overflow-x: auto;
  padding-bottom: 8px;
  scroll-snap-type: x proximity;
}

.route-node {
  min-width: 300px;
  max-width: 340px;
  flex: 0 0 32%;
  scroll-snap-align: start;
}

.route-track {
  display: flex;
  align-items: center;
  min-height: 46px;
}

.route-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 3px solid #dcdfe6;
  border-radius: 999px;
  background: #fff;
  color: #606266;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 3px 10px rgba(31, 45, 61, 0.08);
}

.route-node.is-current .route-dot {
  border-color: #e6a23c;
  color: #e6a23c;
}

.route-node.is-completed .route-dot {
  border-color: #67c23a;
  background: #67c23a;
  color: #fff;
}

.route-connector {
  position: relative;
  height: 6px;
  flex: 1;
  min-width: 96px;
  margin: 0 10px;
  border-radius: 999px;
  background: #dcdfe6;
  overflow: hidden;
}

.route-connector-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, #67c23a, #409eff);
}

.milestone-card {
  width: calc(100% - 22px);
  min-height: 286px;
  margin-right: 22px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}

.milestone-card:hover {
  border-color: #409eff;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.12);
  transform: translateY(-2px);
}

.milestone-kicker,
.milestone-focus,
.milestone-footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.milestone-kicker {
  color: #909399;
  font-size: 12px;
  font-weight: 600;
}

.milestone-title {
  margin-top: 12px;
  color: #303133;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.milestone-course {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.milestone-focus {
  margin-top: 14px;
  color: #409eff;
  font-size: 13px;
  font-weight: 600;
}

.milestone-reason {
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.milestone-progress {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  color: #606266;
  font-size: 13px;
}

.milestone-footer {
  margin-top: 14px;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.milestone-action {
  color: #409eff;
  font-weight: 600;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .embedded-header {
    align-items: stretch;
    flex-direction: column;
  }

  .route-summary {
    flex-direction: column;
  }

  .summary-time {
    flex-shrink: 1;
  }

  .empty-actions,
  .milestone-kicker,
  .milestone-focus,
  .milestone-footer {
    flex-direction: column;
  }

  .route-board {
    padding: 14px;
  }

  .route-scroll {
    flex-direction: column;
    gap: 16px;
    overflow-x: visible;
    padding-bottom: 0;
  }

  .route-node {
    min-width: 0;
    max-width: none;
    width: 100%;
    flex: 1 1 auto;
  }

  .route-track {
    min-height: 34px;
  }

  .route-dot {
    width: 32px;
    height: 32px;
  }

  .route-connector {
    display: none;
  }

  .milestone-card {
    width: 100%;
    min-height: 0;
    margin-right: 0;
  }
}
</style>

<template>
  <div class="learn-page" v-loading="loading">
    <div class="learn-header">
      <div class="learn-header-main">
        <el-button text :icon="ArrowLeft" @click="$router.back()">返回课程</el-button>
        <span class="resource-title">{{ resource?.title }}</span>
      </div>
      <el-button v-if="resourceUrl" text @click="downloadResource">下载资源</el-button>
    </div>

    <div class="learn-body">
      <div v-if="resource?.type === 'VIDEO'" class="video-area">
        <video
          ref="videoEl"
          class="video-player"
          controls
          preload="metadata"
          playsinline
          @timeupdate="handleTimeUpdate"
          @ended="handleEnded"
        />
      </div>
      <div v-else class="doc-area">
        <iframe v-if="resourceUrl" :src="resourceUrl" class="doc-frame" />
      </div>

      <div class="learn-sidebar">
        <el-card shadow="never" :header="sidebarTitle">
          <el-progress type="circle" :percentage="progressPct" />
          <div class="progress-text">{{ sidebarText }}</div>
        </el-card>
        <el-card shadow="never" header="课程目录" class="outline-card">
          <div v-for="chapter in chapters" :key="chapter.id" class="outline-chapter">
            <div class="outline-chapter-title">{{ chapter.orderIndex }}. {{ chapter.title }}</div>
            <div
              v-for="chapterResource in resourceMap[chapter.id] ?? []"
              :key="chapterResource.id"
              class="outline-resource"
              :class="{ active: chapterResource.id === resource?.id }"
              @click="$router.push(`/courses/${route.params.courseId}/learn/${chapterResource.id}`)"
            >
              <span>{{ chapterResource.title }}</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { useUserStore } from '@/store/user'
import type { Chapter, LearningProgress, Resource } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const videoEl = ref<HTMLVideoElement>()
const loading = ref(false)
const resource = ref<Resource | null>(null)
const resourceUrl = ref('')
const progress = ref<LearningProgress | null>(null)
const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})

const canTrackProgress = computed(() => userStore.isStudent)
const progressPct = computed(() => {
  const value = progress.value?.progress ?? 0
  return Math.min(100, Math.max(0, Math.round(value * 100)))
})
const sidebarTitle = computed(() => (canTrackProgress.value ? '学习进度' : '资源预览'))
const sidebarText = computed(() => (
  canTrackProgress.value
    ? `${progressPct.value}% 已完成`
    : '当前为教师预览模式，不记录学习进度'
))

let saveTimer: ReturnType<typeof setInterval> | null = null
let temporaryResourceUrl = ''

function resolveResourceUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }

  if (url.startsWith('/')) {
    return url
  }

  return `/${url}`
}

function revokeTemporaryResourceUrl() {
  if (temporaryResourceUrl) {
    URL.revokeObjectURL(temporaryResourceUrl)
    temporaryResourceUrl = ''
  }
}

function isInternalResourceUrl(url: string) {
  return url.startsWith('/api/')
}

async function buildAuthorizedResourceUrl(url: string) {
  const token = localStorage.getItem('token')
  const response = await fetch(resolveResourceUrl(url), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })

  if (!response.ok) {
    throw new Error('Failed to load resource content')
  }

  const blob = await response.blob()
  revokeTemporaryResourceUrl()
  temporaryResourceUrl = URL.createObjectURL(blob)
  return temporaryResourceUrl
}

function getCourseId() {
  return Number(route.params.courseId)
}

async function handleTimeUpdate() {
  if (!canTrackProgress.value || !videoEl.value || !resource.value) {
    return
  }

  const duration = videoEl.value.duration || 1
  const currentProgress = videoEl.value.currentTime / duration

  if (progress.value) {
    progress.value.progress = currentProgress
  }
}

async function handleEnded() {
  if (!canTrackProgress.value || !resource.value) {
    return
  }

  const saved = await learnApi.updateProgress(String(resource.value.id), {
    courseId: getCourseId(),
    progress: 1,
    lastPosition: videoEl.value ? Math.floor(videoEl.value.currentTime) : undefined,
  })

  progress.value = saved
}

async function saveProgress() {
  if (!canTrackProgress.value || !resource.value) {
    return
  }

  const isVideo = resource.value.type === 'VIDEO'
  const percent = isVideo && videoEl.value
    ? videoEl.value.currentTime / (videoEl.value.duration || 1)
    : 1

  const saved = await learnApi.updateProgress(String(resource.value.id), {
    courseId: getCourseId(),
    progress: percent,
    lastPosition: isVideo && videoEl.value ? Math.floor(videoEl.value.currentTime) : undefined,
  })

  progress.value = saved
}

async function loadCurriculum() {
  const chapterList = await courseApi.listChapters(String(route.params.courseId))
  chapters.value = chapterList

  const resourceEntries = await Promise.all(
    chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const),
  )
  resourceMap.value = Object.fromEntries(resourceEntries)
}

async function loadResourcePage() {
  const resourceId = String(route.params.resourceId)
  const resourceData = await courseApi.getResource(resourceId)
  const progressData = canTrackProgress.value
    ? await learnApi.getProgress(resourceId).catch(() => null)
    : null

  resource.value = resourceData ?? null
  progress.value = progressData

  if (!resourceData) {
    resourceUrl.value = ''
    return
  }

  const resolvedUrl = resolveResourceUrl(resourceData.url)
  if (isInternalResourceUrl(resolvedUrl)) {
    resourceUrl.value = await buildAuthorizedResourceUrl(resolvedUrl)
  } else {
    revokeTemporaryResourceUrl()
    resourceUrl.value = resolvedUrl
  }

  await nextTick()

  if (resourceData.type === 'VIDEO' && videoEl.value) {
    videoEl.value.src = resourceUrl.value
    videoEl.value.load()
    restoreVideoPosition(videoEl.value, progressData?.lastPosition)
    return
  }

  if (canTrackProgress.value && !progressData?.completed) {
    progress.value = await learnApi.updateProgress(String(resourceData.id), {
      courseId: getCourseId(),
      progress: 1,
    })
  }
}

function handleBeforeUnload() {
  if (canTrackProgress.value) {
    saveProgress().catch(() => undefined)
  }
}

function downloadResource() {
  if (!resourceUrl.value || !resource.value) {
    return
  }

  const link = document.createElement('a')
  link.href = resourceUrl.value
  link.download = resource.value.title
  link.target = '_blank'
  link.rel = 'noopener'
  link.click()
}

function restoreVideoPosition(player: HTMLVideoElement, lastPosition?: number) {
  if (!lastPosition || lastPosition <= 0) {
    return
  }

  const applyPosition = () => {
    if (Number.isFinite(player.duration) && player.duration > 0) {
      player.currentTime = Math.min(lastPosition, Math.max(player.duration - 1, 0))
      return
    }

    player.currentTime = lastPosition
  }

  if (player.readyState >= 1) {
    applyPosition()
    return
  }

  player.addEventListener('loadedmetadata', applyPosition, { once: true })
}

onMounted(async () => {
  loading.value = true

  try {
    await loadCurriculum()
    await loadResourcePage()

    if (canTrackProgress.value) {
      saveTimer = setInterval(() => {
        saveProgress().catch(() => undefined)
      }, 10000)
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
  } finally {
    loading.value = false
  }
})

watch(
  () => `${route.params.courseId}:${route.params.resourceId}`,
  async () => {
    loading.value = true
    try {
      if (canTrackProgress.value) {
        await saveProgress().catch(() => undefined)
      }
      await loadCurriculum()
      await loadResourcePage()
    } finally {
      loading.value = false
    }
  },
)

onBeforeUnmount(() => {
  if (saveTimer) {
    clearInterval(saveTimer)
  }

  window.removeEventListener('beforeunload', handleBeforeUnload)
  if (canTrackProgress.value) {
    saveProgress().catch(() => undefined)
  }
  revokeTemporaryResourceUrl()
})
</script>

<style scoped>
.learn-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #1a1a2e;
}

.learn-header {
  height: 56px;
  background: #16213e;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 16px;
  color: #fff;
  justify-content: space-between;
}

.learn-header-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.resource-title {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.learn-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.video-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
}

.video-player {
  width: 100%;
  max-height: 100%;
}

.doc-area {
  flex: 1;
  background: #fff;
}

.doc-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.learn-sidebar {
  width: 280px;
  background: #fff;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-text {
  text-align: center;
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
}

.outline-card {
  flex: 1;
}

.outline-chapter + .outline-chapter {
  margin-top: 16px;
}

.outline-chapter-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.outline-resource {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 13px;
}

.outline-resource:hover,
.outline-resource.active {
  background: #ecf5ff;
  color: #409eff;
}
</style>

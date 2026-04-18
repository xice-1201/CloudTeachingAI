<template>
  <div class="learn-page" v-loading="loading">
    <div class="learn-header">
      <el-button text :icon="ArrowLeft" @click="$router.back()">返回课程</el-button>
      <span class="resource-title">{{ resource?.title }}</span>
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
        <el-card shadow="never" header="学习进度">
          <el-progress type="circle" :percentage="progressPct" />
          <div class="progress-text">{{ progressPct }}% 已完成</div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import type { LearningProgress, Resource } from '@/types'

const route = useRoute()
const videoEl = ref<HTMLVideoElement>()
const loading = ref(false)
const resource = ref<Resource | null>(null)
const resourceUrl = ref('')
const progress = ref<LearningProgress | null>(null)

const progressPct = computed(() => {
  const value = progress.value?.progress ?? 0
  return Math.min(100, Math.max(0, Math.round(value * 100)))
})

let saveTimer: ReturnType<typeof setInterval> | null = null

function resolveResourceUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }

  if (url.startsWith('/')) {
    return url
  }

  return `/${url}`
}

function getCourseId() {
  return Number(route.params.courseId)
}

async function handleTimeUpdate() {
  if (!videoEl.value || !resource.value) {
    return
  }

  const duration = videoEl.value.duration || 1
  const currentProgress = videoEl.value.currentTime / duration

  if (progress.value) {
    progress.value.progress = currentProgress
  }
}

async function handleEnded() {
  if (!resource.value) {
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
  if (!resource.value) {
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
    const resourceId = String(route.params.resourceId)
    const [resourceData, progressData] = await Promise.all([
      courseApi.getResource(resourceId),
      learnApi.getProgress(resourceId).catch(() => null),
    ])

    resource.value = resourceData ?? null
    progress.value = progressData

    if (!resourceData) {
      return
    }

    const url = resolveResourceUrl(resourceData.url)
    resourceUrl.value = url

    await nextTick()

    if (resourceData.type === 'VIDEO' && videoEl.value) {
      videoEl.value.src = url
      videoEl.value.load()
      restoreVideoPosition(videoEl.value, progressData?.lastPosition)
    } else if (!progressData?.completed) {
      progress.value = await learnApi.updateProgress(String(resourceData.id), {
        courseId: getCourseId(),
        progress: 1,
      })
    }

    saveTimer = setInterval(() => {
      saveProgress().catch(() => undefined)
    }, 10000)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (saveTimer) {
    clearInterval(saveTimer)
  }

  saveProgress().catch(() => undefined)
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
}

.progress-text {
  text-align: center;
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}
</style>

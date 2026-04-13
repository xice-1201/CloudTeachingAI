<template>
  <div class="learn-page" v-loading="loading">
    <div class="learn-header">
      <el-button text :icon="ArrowLeft" @click="$router.back()">返回课程</el-button>
      <span class="resource-title">{{ resource?.title }}</span>
    </div>

    <div class="learn-body">
      <div class="video-area" v-if="resource?.type === 'VIDEO'">
        <video ref="videoEl" class="video-player" controls @timeupdate="handleTimeUpdate" @ended="handleEnded" />
      </div>
      <div class="doc-area" v-else>
        <iframe v-if="resourceUrl" :src="resourceUrl" class="doc-frame" />
      </div>

      <div class="learn-sidebar">
        <el-card shadow="never" header="学习进度">
          <el-progress type="circle" :percentage="progressPct" />
          <div style="text-align: center; margin-top: 12px; color: #909399; font-size: 13px">
            {{ progressPct }}% 已完成
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { mediaApi } from '@/api/media'
import type { Resource, LearningProgress } from '@/types'

const route = useRoute()
const videoEl = ref<HTMLVideoElement>()
const loading = ref(false)
const resource = ref<Resource | null>(null)
const resourceUrl = ref('')
const progress = ref<LearningProgress | null>(null)

const progressPct = computed(() => progress.value ? Math.round(progress.value.progress * 100) : 0)

let saveTimer: ReturnType<typeof setInterval> | null = null

async function handleTimeUpdate() {
  if (!videoEl.value || !resource.value) return
  const pct = videoEl.value.currentTime / (videoEl.value.duration || 1)
  if (progress.value) progress.value.progress = pct
}

async function handleEnded() {
  if (!resource.value) return
  await learnApi.updateProgress(resource.value.id, { progress: 1 })
  if (progress.value) { progress.value.progress = 1; progress.value.completed = true }
}

async function saveProgress() {
  if (!resource.value || !videoEl.value) return
  const pct = videoEl.value.currentTime / (videoEl.value.duration || 1)
  await learnApi.updateProgress(resource.value.id, {
    progress: pct,
    lastPosition: Math.floor(videoEl.value.currentTime),
  })
}

onMounted(async () => {
  loading.value = true
  try {
    const resourceId = route.params.resourceId as string
    const [res, prog] = await Promise.all([
      courseApi.getResource(resourceId),
      learnApi.getProgress(resourceId).catch(() => null),
    ])
    resource.value = res ?? null
    progress.value = prog

    if (res) {
      const { url } = await mediaApi.getPresignedUrl(res.url)
      resourceUrl.value = url
      if (res.type === 'VIDEO' && videoEl.value) {
        videoEl.value.src = url
        if (prog?.lastPosition) videoEl.value.currentTime = prog.lastPosition
      }
    }

    saveTimer = setInterval(saveProgress, 10000)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (saveTimer) clearInterval(saveTimer)
  saveProgress()
})
</script>

<style scoped>
.learn-page { display: flex; flex-direction: column; height: 100vh; background: #1a1a2e; }
.learn-header {
  height: 56px;
  background: #16213e;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 16px;
  color: #fff;
}
.resource-title { font-size: 15px; font-weight: 600; color: #fff; }
.learn-body { flex: 1; display: flex; overflow: hidden; }
.video-area { flex: 1; display: flex; align-items: center; justify-content: center; background: #000; }
.video-player { width: 100%; max-height: 100%; }
.doc-area { flex: 1; background: #fff; }
.doc-frame { width: 100%; height: 100%; border: none; }
.learn-sidebar { width: 280px; background: #fff; padding: 16px; overflow-y: auto; }
</style>

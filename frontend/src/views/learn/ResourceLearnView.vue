<template>
  <div class="learn-page" v-loading="loading">
    <div class="learn-header">
      <div class="learn-header-main">
        <el-button text :icon="ArrowLeft" @click="$router.back()">返回课程</el-button>
        <span class="resource-title">{{ resource?.title }}</span>
      </div>
      <div class="learn-header-actions">
        <el-button v-if="resource" text :icon="ChatDotRound" @click="askAiForResource">问 AI</el-button>
        <el-button text @click="scrollToDiscussions">讨论 {{ resourceDiscussions.length }}</el-button>
        <el-button v-if="resourceUrl" text @click="downloadResource">下载资源</el-button>
      </div>
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

        <el-card shadow="never" header="资源信息">
          <div v-if="resource?.description" class="resource-description">{{ resource.description }}</div>
          <div v-if="resource?.tags?.length || resource?.knowledgePoints?.length" class="resource-tags">
            <el-tag
              v-for="tag in (resource?.tags?.length ? resource.tags : resource?.knowledgePoints)"
              :key="`${resource?.id}-${resourceTagKey(tag)}`"
              size="small"
              effect="plain"
            >
              {{ resourceTagLabel(tag) }}
            </el-tag>
          </div>
          <div v-else class="progress-text">当前资源还没有确认的知识点标签。</div>
        </el-card>

        <el-card id="discussions" shadow="never" header="资源讨论">
          <div class="discussion-editor">
            <el-input
              v-model="resourceDiscussionForm.title"
              placeholder="讨论主题"
              maxlength="255"
              show-word-limit
            />
            <el-input
              v-model="resourceDiscussionForm.content"
              type="textarea"
              :rows="3"
              placeholder="围绕当前资源提问、交流理解或补充说明"
            />
            <el-button type="primary" :loading="discussionSubmitting" @click="submitResourceDiscussion">发布讨论</el-button>
          </div>

          <div v-if="resourceDiscussions.length === 0" class="progress-text">当前还没有资源讨论。</div>
          <div v-else class="discussion-list">
            <article v-for="post in resourceDiscussions" :key="post.id" class="discussion-item">
              <div class="discussion-title">{{ post.title }}</div>
              <div class="discussion-meta">
                <span>{{ post.authorName }}</span>
                <span>{{ formatDate(post.createdAt) }}</span>
              </div>
              <div class="discussion-content">{{ post.content }}</div>
              <div class="discussion-actions">
                <el-button link type="primary" @click="toggleReply(post.id)">回复</el-button>
                <el-button v-if="canDeleteDiscussion(post)" link type="danger" @click="deleteDiscussion(post.id)">删除</el-button>
              </div>

              <div v-if="replyingToId === post.id" class="reply-editor">
                <el-input v-model="replyContent" type="textarea" :rows="3" placeholder="写下你的回复" />
                <div class="discussion-actions">
                  <el-button @click="cancelReply">取消</el-button>
                  <el-button type="primary" :loading="discussionSubmitting" @click="submitReply(post.id)">提交回复</el-button>
                </div>
              </div>

              <div v-if="post.replies.length" class="reply-list">
                <div v-for="reply in post.replies" :key="reply.id" class="reply-item">
                  <div class="discussion-meta">
                    <span>{{ reply.authorName }}</span>
                    <span>{{ formatDate(reply.createdAt) }}</span>
                  </div>
                  <div class="discussion-content">{{ reply.content }}</div>
                  <div class="discussion-actions">
                    <el-button v-if="canDeleteDiscussion(reply)" link type="danger" @click="deleteDiscussion(reply.id)">删除</el-button>
                  </div>
                </div>
              </div>
            </article>
          </div>
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
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { useUserStore } from '@/store/user'
import type { Chapter, DiscussionPost, LearningProgress, Resource, ResourceKnowledgePoint, ResourceTag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const videoEl = ref<HTMLVideoElement>()
const loading = ref(false)
const discussionSubmitting = ref(false)
const resource = ref<Resource | null>(null)
const resourceUrl = ref('')
const progress = ref<LearningProgress | null>(null)
const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const resourceDiscussions = ref<DiscussionPost[]>([])
const replyingToId = ref<number | null>(null)
const replyContent = ref('')

const resourceDiscussionForm = ref({
  title: '',
  content: '',
})

const canTrackProgress = computed(() => userStore.isStudent)
const progressPct = computed(() => Math.min(100, Math.max(0, Math.round((progress.value?.progress ?? 0) * 100))))
const sidebarTitle = computed(() => (canTrackProgress.value ? '学习进度' : '资源预览'))
const sidebarText = computed(() => (
  canTrackProgress.value
    ? `${progressPct.value}% 已完成`
    : '当前为教师预览模式，不记录学习进度。'
))

let saveTimer: ReturnType<typeof setInterval> | null = null
let temporaryResourceUrl = ''

function resolveResourceUrl(url: string) {
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('/')) return url
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

function buildDownloadUrl(url: string) {
  const resolvedUrl = resolveResourceUrl(url)
  if (!isInternalResourceUrl(resolvedUrl)) return resolvedUrl
  const separator = resolvedUrl.includes('?') ? '&' : '?'
  return `${resolvedUrl}${separator}download=true`
}

function getCourseId() {
  return Number(route.params.courseId)
}

function askAiForResource() {
  if (!resource.value) return
  const knowledgePoint = resource.value.knowledgePoints?.[0]
  const tag = resource.value.tags?.[0]
  router.push({
    name: 'Chat',
    query: {
      courseId: route.params.courseId,
      resourceId: resource.value.id,
      resourceTitle: resource.value.title,
      knowledgePointId: knowledgePoint?.id ?? tag?.knowledgePointId ?? undefined,
      knowledgePointName: knowledgePoint?.name ?? tag?.label ?? undefined,
      returnUrl: `/courses/${route.params.courseId}/learn/${resource.value.id}`,
      returnLabel: '返回资源',
    },
  })
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function scrollToDiscussions() {
  document.getElementById('discussions')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function resourceTagLabel(tag: ResourceTag | ResourceKnowledgePoint) {
  return 'label' in tag ? tag.label : tag.name
}

function resourceTagKey(tag: ResourceTag | ResourceKnowledgePoint) {
  return 'label' in tag ? tag.label : tag.id
}

function canDeleteDiscussion(post: DiscussionPost) {
  return userStore.isAdmin || post.authorId === userStore.user?.id
}

async function buildAuthorizedResourceUrl(url: string) {
  const token = localStorage.getItem('token')
  const response = await fetch(resolveResourceUrl(url), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error('Failed to load resource content')
  const blob = await response.blob()
  revokeTemporaryResourceUrl()
  temporaryResourceUrl = URL.createObjectURL(blob)
  return temporaryResourceUrl
}

function handleTimeUpdate() {
  if (!canTrackProgress.value || !videoEl.value || !resource.value) return
  const duration = videoEl.value.duration || 1
  if (progress.value) progress.value.progress = videoEl.value.currentTime / duration
}

async function handleEnded() {
  if (!canTrackProgress.value || !resource.value) return
  progress.value = await learnApi.updateProgress(String(resource.value.id), {
    courseId: getCourseId(),
    progress: 1,
    lastPosition: videoEl.value ? Math.floor(videoEl.value.currentTime) : undefined,
  })
}

async function saveProgress() {
  if (!canTrackProgress.value || !resource.value) return
  const isVideo = resource.value.type === 'VIDEO'
  const percent = isVideo && videoEl.value ? videoEl.value.currentTime / (videoEl.value.duration || 1) : 1
  progress.value = await learnApi.updateProgress(String(resource.value.id), {
    courseId: getCourseId(),
    progress: percent,
    lastPosition: isVideo && videoEl.value ? Math.floor(videoEl.value.currentTime) : undefined,
  })
}

async function loadCurriculum() {
  const chapterList = await courseApi.listChapters(String(route.params.courseId))
  chapters.value = chapterList
  const resourceEntries = await Promise.all(chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const))
  resourceMap.value = Object.fromEntries(resourceEntries)
}

async function loadResourcePage() {
  const resourceId = String(route.params.resourceId)
  const resourceData = await courseApi.getResource(resourceId)
  const progressData = canTrackProgress.value ? await learnApi.getProgress(resourceId).catch(() => null) : null
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

async function loadResourceDiscussions() {
  resourceDiscussions.value = await courseApi.listDiscussions(String(route.params.courseId), {
    resourceId: Number(route.params.resourceId),
  }).catch(() => [])
}

function handleBeforeUnload() {
  if (canTrackProgress.value) saveProgress().catch(() => undefined)
}

async function downloadResource() {
  if (!resourceUrl.value || !resource.value) return
  const downloadUrl = buildDownloadUrl(resource.value.url)
  if (isInternalResourceUrl(resolveResourceUrl(resource.value.url))) {
    const token = localStorage.getItem('token')
    const response = await fetch(downloadUrl, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      ElMessage.error('资源下载失败，请稍后重试')
      return
    }
    const blob = await response.blob()
    const temporaryDownloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = temporaryDownloadUrl
    link.download = resource.value.title
    link.click()
    URL.revokeObjectURL(temporaryDownloadUrl)
    return
  }

  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = resource.value.title
  link.target = '_blank'
  link.rel = 'noopener'
  link.click()
}

function restoreVideoPosition(player: HTMLVideoElement, lastPosition?: number) {
  if (!lastPosition || lastPosition <= 0) return
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

function toggleReply(postId: number) {
  replyingToId.value = replyingToId.value === postId ? null : postId
  replyContent.value = ''
}

function cancelReply() {
  replyingToId.value = null
  replyContent.value = ''
}

async function submitResourceDiscussion() {
  if (!resource.value || !resourceDiscussionForm.value.title.trim() || !resourceDiscussionForm.value.content.trim()) {
    ElMessage.warning('请填写讨论主题和内容')
    return
  }
  discussionSubmitting.value = true
  try {
    await courseApi.createDiscussion(String(route.params.courseId), {
      resourceId: resource.value.id,
      title: resourceDiscussionForm.value.title,
      content: resourceDiscussionForm.value.content,
    })
    resourceDiscussionForm.value.title = ''
    resourceDiscussionForm.value.content = ''
    ElMessage.success('讨论已发布')
    await loadResourceDiscussions()
  } finally {
    discussionSubmitting.value = false
  }
}

async function submitReply(parentId: number) {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  discussionSubmitting.value = true
  try {
    await courseApi.createDiscussion(String(route.params.courseId), {
      parentId,
      content: replyContent.value,
    })
    cancelReply()
    ElMessage.success('回复已发布')
    await loadResourceDiscussions()
  } finally {
    discussionSubmitting.value = false
  }
}

async function deleteDiscussion(discussionId: number) {
  await ElMessageBox.confirm('确定删除这条讨论吗？删除后其回复也会一并移除。', '删除讨论', { type: 'warning' })
  await courseApi.deleteDiscussion(String(discussionId))
  ElMessage.success('讨论已删除')
  await loadResourceDiscussions()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCurriculum()
    await loadResourcePage()
    await loadResourceDiscussions()
    if (route.hash === '#discussions') {
      setTimeout(scrollToDiscussions, 100)
    }
    if (canTrackProgress.value) {
      saveTimer = setInterval(() => { saveProgress().catch(() => undefined) }, 10000)
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
  } finally {
    loading.value = false
  }
})

watch(() => `${route.params.courseId}:${route.params.resourceId}`, async () => {
  loading.value = true
  try {
    if (canTrackProgress.value) await saveProgress().catch(() => undefined)
    await loadCurriculum()
    await loadResourcePage()
    await loadResourceDiscussions()
    if (route.hash === '#discussions') {
      setTimeout(scrollToDiscussions, 100)
    }
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (saveTimer) clearInterval(saveTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  if (canTrackProgress.value) saveProgress().catch(() => undefined)
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
  justify-content: space-between;
  padding: 0 20px;
  gap: 16px;
  color: #fff;
}

.learn-header-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.learn-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  width: 340px;
  background: #fff;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-text,
.resource-description,
.discussion-meta {
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
}

.resource-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.discussion-editor,
.reply-editor {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.discussion-list {
  display: grid;
  gap: 12px;
}

.discussion-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
}

.discussion-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.discussion-content {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.discussion-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.reply-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.reply-item {
  padding: 10px;
  border-radius: 8px;
  background: #fff;
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

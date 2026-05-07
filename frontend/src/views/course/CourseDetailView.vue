<template>
  <div class="page-container" v-loading="loading">
    <template v-if="course">
      <div class="page-header">
        <div class="page-title-wrap">
          <span class="page-title">{{ course.title }}</span>
          <el-tag :type="statusTagType(course.status)">{{ statusLabel(course.status) }}</el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="course" :icon="ChatDotRound" @click="askAiForCourse">问 AI</el-button>
          <el-button v-if="canEdit" @click="$router.push(`/courses/${course.id}/edit`)">编辑课程</el-button>
          <el-button v-if="canPublish" type="primary" @click="handleLifecycle('publish')">发布课程</el-button>
          <el-button v-if="canUnpublish" @click="handleLifecycle('unpublish')">撤回发布</el-button>
          <el-button v-if="canRestore" @click="handleLifecycle('restore')">恢复为草稿</el-button>
          <el-button v-if="canArchive" @click="handleLifecycle('archive')">归档课程</el-button>
          <el-button v-if="canEnroll" type="primary" @click="handleEnroll">选课</el-button>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :xs="24" :lg="16">
          <el-alert
            v-if="showEnrollHint"
            type="info"
            :closable="false"
            title="当前可以查看课程简介和公告，选课后才可以访问章节资源和参与讨论。"
            class="section-gap"
          />

          <el-card shadow="never" class="section-gap">
            <template #header>
              <div class="section-header">
                <span>课程公告</span>
                <el-button v-if="canEdit" type="primary" link @click="openAnnouncementDialog()">发布公告</el-button>
              </div>
            </template>

            <div v-if="announcements.length === 0" class="empty-tip">当前还没有课程公告。</div>
            <div v-else class="announcement-list">
              <article v-for="item in announcements" :key="item.id" class="announcement-item">
                <div class="announcement-head">
                  <div>
                    <div class="announcement-title">
                      <span>{{ item.title }}</span>
                      <el-tag v-if="item.pinned" size="small" type="danger">置顶</el-tag>
                    </div>
                    <div class="announcement-meta">
                      <span>{{ item.authorName }}</span>
                      <span>{{ formatDate(item.publishedAt) }}</span>
                    </div>
                  </div>
                  <div v-if="canEdit" class="announcement-actions">
                    <el-button link type="primary" @click="openAnnouncementDialog(item)">编辑</el-button>
                    <el-button link type="danger" @click="deleteAnnouncement(item.id)">删除</el-button>
                  </div>
                </div>
                <div class="announcement-content">{{ item.content }}</div>
              </article>
            </div>
          </el-card>

          <el-card shadow="never" class="section-gap" header="课程章节">
            <div v-if="!contentAccessGranted" class="empty-tip">当前还不能访问课程内容。</div>
            <div v-else-if="chapters.length === 0" class="empty-tip">当前课程还没有章节。</div>
            <el-collapse v-else>
              <el-collapse-item
                v-for="chapter in chapters"
                :key="chapter.id"
                :title="`第 ${chapter.orderIndex} 章 · ${chapter.title}`"
                :name="chapter.id"
              >
                <div v-if="chapter.description" class="chapter-description">{{ chapter.description }}</div>
                <div
                  v-for="resource in resourceMap[chapter.id] ?? []"
                  :key="resource.id"
                  class="resource-item"
                  @click="$router.push(`/courses/${course.id}/learn/${resource.id}`)"
                >
                  <div class="resource-main">
                    <div class="resource-head">
                      <el-icon><component :is="resourceIcon(resource.type)" /></el-icon>
                      <span>{{ resource.title }}</span>
                      <el-tag size="small">{{ resourceTypeLabel(resource.type) }}</el-tag>
                    </div>
                    <div v-if="resource.description" class="resource-description">{{ resource.description }}</div>
                    <div v-if="resource.tags?.length || resource.knowledgePoints?.length" class="resource-tags">
                      <el-tag
                        v-for="tag in (resource.tags?.length ? resource.tags : resource.knowledgePoints)"
                        :key="`${resource.id}-${resourceTagKey(tag)}`"
                        size="small"
                        effect="plain"
                      >
                        {{ resourceTagLabel(tag) }}
                      </el-tag>
                    </div>
                  </div>
                  <span v-if="resource.duration" class="resource-duration">{{ formatDuration(resource.duration) }}</span>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-card>

          <el-card id="discussions" shadow="never" header="课程讨论">
            <template #header>
              <div class="section-header">
                <span>课程讨论</span>
                <span class="section-subtitle">围绕本课程整体交流问题、想法和学习心得</span>
              </div>
            </template>

            <div v-if="canParticipateDiscussion" class="discussion-editor">
              <el-input v-model="discussionForm.title" placeholder="讨论主题" maxlength="255" show-word-limit />
              <el-input
                v-model="discussionForm.content"
                type="textarea"
                :rows="4"
                placeholder="输入你想和老师、同学讨论的内容"
              />
              <div class="discussion-actions">
                <el-button type="primary" :loading="discussionSubmitting" @click="submitDiscussion">发布讨论</el-button>
              </div>
            </div>
            <div v-else class="empty-tip">选课后即可参与课程讨论。</div>

            <div v-if="generalDiscussions.length === 0" class="empty-tip">当前还没有课程讨论。</div>
            <div v-else class="discussion-list">
              <article v-for="post in generalDiscussions" :key="post.id" class="discussion-item">
                <div class="discussion-main">
                  <div class="discussion-title">{{ post.title }}</div>
                  <div class="discussion-meta">
                    <span>{{ post.authorName }}</span>
                    <span>{{ formatDate(post.createdAt) }}</span>
                  </div>
                  <div class="discussion-content">{{ post.content }}</div>
                </div>
                <div class="discussion-toolbar">
                  <el-button v-if="canParticipateDiscussion" link type="primary" @click="toggleReply(post.id)">回复</el-button>
                  <el-button v-if="canDeleteDiscussion(post)" link type="danger" @click="deleteDiscussion(post.id)">删除</el-button>
                </div>

                <div v-if="replyingToId === post.id" class="reply-editor">
                  <el-input
                    v-model="replyContent"
                    type="textarea"
                    :rows="3"
                    placeholder="写下你的回复"
                  />
                  <div class="discussion-actions">
                    <el-button @click="cancelReply">取消</el-button>
                    <el-button type="primary" :loading="discussionSubmitting" @click="submitReply(post.id)">提交回复</el-button>
                  </div>
                </div>

                <div v-if="post.replies.length" class="reply-list">
                  <div v-for="reply in post.replies" :key="reply.id" class="reply-item">
                    <div class="reply-meta">
                      <span>{{ reply.authorName }}</span>
                      <span>{{ formatDate(reply.createdAt) }}</span>
                    </div>
                    <div class="reply-content">{{ reply.content }}</div>
                    <div class="reply-actions">
                      <el-button v-if="canDeleteDiscussion(reply)" link type="danger" @click="deleteDiscussion(reply.id)">删除</el-button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <el-card shadow="never">
            <template #header>
              <span>课程信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="教师">{{ course.teacherName }}</el-descriptions-item>
              <el-descriptions-item label="可见范围">{{ visibilityLabel(course) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDate(course.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDate(course.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
            <div class="desc-section">
              <div class="desc-label">课程描述</div>
              <p class="desc-text">{{ course.description }}</p>
            </div>
          </el-card>
          <el-card shadow="never" class="section-gap">
            <template #header>
              <div class="section-header">
                <span>最近讨论</span>
                <el-button link type="primary" @click="scrollToDiscussions">查看全部</el-button>
              </div>
            </template>
            <div v-if="recentDiscussions.length === 0" class="empty-tip">暂无讨论动态。</div>
            <div v-else class="recent-discussion-list">
              <button
                v-for="post in recentDiscussions"
                :key="post.id"
                class="recent-discussion-item"
                type="button"
                @click="scrollToDiscussions"
              >
                <span class="recent-discussion-title">{{ post.title }}</span>
                <span class="recent-discussion-meta">{{ post.replies.length }} 条回复</span>
              </button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-dialog
      v-model="announcementDialog.visible"
      :title="announcementDialog.id ? '编辑公告' : '发布公告'"
      width="640px"
    >
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="announcementDialog.title" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="announcementDialog.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="announcementDialog.pinned" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announcementDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="announcementSubmitting" @click="submitAnnouncement">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Document, Paperclip, VideoPlay } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Announcement, Chapter, Course, DiscussionPost, Resource, ResourceKnowledgePoint, ResourceTag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const announcementSubmitting = ref(false)
const discussionSubmitting = ref(false)
const course = ref<Course | null>(null)
const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const announcements = ref<Announcement[]>([])
const generalDiscussions = ref<DiscussionPost[]>([])
const contentAccessGranted = ref(false)
const replyingToId = ref<number | null>(null)
const replyContent = ref('')

const discussionForm = ref({
  title: '',
  content: '',
})

const announcementDialog = ref({
  visible: false,
  id: null as number | null,
  title: '',
  content: '',
  pinned: false,
})

const canEdit = computed(() => Boolean(course.value) && (userStore.isAdmin || (!userStore.isStudent && course.value?.teacherId === userStore.user?.id)))
const canPublish = computed(() => canEdit.value && course.value?.status === 'DRAFT')
const canUnpublish = computed(() => canEdit.value && course.value?.status === 'PUBLISHED')
const canArchive = computed(() => canEdit.value && course.value?.status !== 'ARCHIVED')
const canRestore = computed(() => canEdit.value && course.value?.status === 'ARCHIVED')
const canEnroll = computed(() => userStore.isStudent && course.value?.status === 'PUBLISHED' && !contentAccessGranted.value)
const showEnrollHint = computed(() => userStore.isStudent && !contentAccessGranted.value && course.value?.status === 'PUBLISHED')
const canParticipateDiscussion = computed(() => contentAccessGranted.value || canEdit.value)
const recentDiscussions = computed(() => generalDiscussions.value.slice(0, 3))

function statusTagType(status: string) {
  return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info'
}

function statusLabel(status: string) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status
}

function visibilityLabel(currentCourse: Course) {
  if (currentCourse.visibilityType === 'SELECTED_STUDENTS') {
    return `定向开放${currentCourse.visibleStudentCount ? ` · ${currentCourse.visibleStudentCount} 名学生` : ''}`
  }
  return '全体学生可见'
}

function resourceTypeLabel(type: string) {
  return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function scrollToDiscussions() {
  document.getElementById('discussions')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function askAiForCourse() {
  if (!course.value) return
  router.push({
    name: 'Chat',
    query: {
      courseId: course.value.id,
      courseTitle: course.value.title,
    },
  })
}

function formatDuration(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function resourceIcon(type: string) {
  return { VIDEO: VideoPlay, DOCUMENT: Document, SLIDE: Paperclip }[type] ?? Document
}

function resourceTagLabel(tag: ResourceTag | ResourceKnowledgePoint) {
  return 'label' in tag ? tag.label : tag.name
}

function resourceTagKey(tag: ResourceTag | ResourceKnowledgePoint) {
  return 'label' in tag ? tag.label : tag.id
}

function openAnnouncementDialog(item?: Announcement) {
  announcementDialog.value.visible = true
  announcementDialog.value.id = item?.id ?? null
  announcementDialog.value.title = item?.title ?? ''
  announcementDialog.value.content = item?.content ?? ''
  announcementDialog.value.pinned = item?.pinned ?? false
}

function resetAnnouncementDialog() {
  announcementDialog.value = {
    visible: false,
    id: null,
    title: '',
    content: '',
    pinned: false,
  }
}

function canDeleteDiscussion(post: DiscussionPost) {
  return canEdit.value || post.authorId === userStore.user?.id
}

function toggleReply(postId: number) {
  replyingToId.value = replyingToId.value === postId ? null : postId
  replyContent.value = ''
}

function cancelReply() {
  replyingToId.value = null
  replyContent.value = ''
}

async function loadCourseSummary() {
  course.value = await courseApi.getCourse(String(route.params.id))
}

async function loadCurriculum() {
  const courseId = String(route.params.id)
  try {
    const chapterList = await courseApi.listChapters(courseId)
    chapters.value = chapterList
    const resources = await Promise.all(chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const))
    resourceMap.value = Object.fromEntries(resources)
    contentAccessGranted.value = true
  } catch (_error) {
    chapters.value = []
    resourceMap.value = {}
    contentAccessGranted.value = false
  }
}

async function loadInteractions() {
  const courseId = String(route.params.id)
  announcements.value = await courseApi.listAnnouncements(courseId).catch(() => [])
  generalDiscussions.value = await courseApi.listDiscussions(courseId).catch(() => [])
}

async function handleLifecycle(action: 'publish' | 'unpublish' | 'archive' | 'restore') {
  if (!course.value) return
  const actionMap = {
    publish: { text: '发布课程', api: courseApi.publishCourse },
    unpublish: { text: '撤回发布', api: courseApi.unpublishCourse },
    archive: { text: '归档课程', api: courseApi.archiveCourse },
    restore: { text: '恢复为草稿', api: courseApi.restoreCourse },
  } as const
  await ElMessageBox.confirm(`确定要${actionMap[action].text}吗？`, actionMap[action].text, { type: 'warning' })
  course.value = await actionMap[action].api(String(course.value.id))
  ElMessage.success(`${actionMap[action].text}成功`)
  await Promise.all([loadCurriculum(), loadInteractions()])
}

async function handleEnroll() {
  if (!course.value) return
  await courseApi.enrollCourse(String(course.value.id))
  ElMessage.success('选课成功')
  await Promise.all([loadCurriculum(), loadInteractions()])
}

async function submitAnnouncement() {
  if (!course.value || !announcementDialog.value.title.trim() || !announcementDialog.value.content.trim()) {
    ElMessage.warning('请完整填写公告标题和内容')
    return
  }
  announcementSubmitting.value = true
  try {
    if (announcementDialog.value.id) {
      await courseApi.updateAnnouncement(String(announcementDialog.value.id), {
        title: announcementDialog.value.title,
        content: announcementDialog.value.content,
        pinned: announcementDialog.value.pinned,
      })
      ElMessage.success('公告已更新')
    } else {
      await courseApi.createAnnouncement(String(course.value.id), {
        title: announcementDialog.value.title,
        content: announcementDialog.value.content,
        pinned: announcementDialog.value.pinned,
      })
      ElMessage.success('公告已发布')
    }
    resetAnnouncementDialog()
    await loadInteractions()
  } finally {
    announcementSubmitting.value = false
  }
}

async function deleteAnnouncement(announcementId: number) {
  await ElMessageBox.confirm('确定删除这条公告吗？', '删除公告', { type: 'warning' })
  await courseApi.deleteAnnouncement(String(announcementId))
  ElMessage.success('公告已删除')
  await loadInteractions()
}

async function submitDiscussion() {
  if (!course.value || !discussionForm.value.title.trim() || !discussionForm.value.content.trim()) {
    ElMessage.warning('请填写讨论主题和内容')
    return
  }
  discussionSubmitting.value = true
  try {
    await courseApi.createDiscussion(String(course.value.id), {
      title: discussionForm.value.title,
      content: discussionForm.value.content,
    })
    discussionForm.value.title = ''
    discussionForm.value.content = ''
    ElMessage.success('讨论已发布')
    await loadInteractions()
  } finally {
    discussionSubmitting.value = false
  }
}

async function submitReply(parentId: number) {
  if (!course.value || !replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  discussionSubmitting.value = true
  try {
    await courseApi.createDiscussion(String(course.value.id), {
      parentId,
      content: replyContent.value,
    })
    cancelReply()
    ElMessage.success('回复已发布')
    await loadInteractions()
  } finally {
    discussionSubmitting.value = false
  }
}

async function deleteDiscussion(discussionId: number) {
  await ElMessageBox.confirm('确定删除这条讨论吗？删除后其回复也会一起移除。', '删除讨论', { type: 'warning' })
  await courseApi.deleteDiscussion(String(discussionId))
  ElMessage.success('讨论已删除')
  await loadInteractions()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCourseSummary()
    await loadCurriculum()
    await loadInteractions()
    if (route.hash === '#discussions') {
      setTimeout(scrollToDiscussions, 100)
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-title-wrap,
.header-actions,
.resource-head,
.resource-tags,
.announcement-actions,
.discussion-actions,
.reply-meta,
.reply-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page-title-wrap {
  gap: 12px;
}

.section-gap {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-subtitle {
  color: #909399;
  font-size: 12px;
}

.announcement-list,
.discussion-list {
  display: grid;
  gap: 16px;
}

.announcement-item,
.discussion-item {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fff;
}

.announcement-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.announcement-title,
.discussion-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.announcement-meta,
.discussion-meta,
.reply-meta {
  margin-top: 6px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 12px;
}

.announcement-content,
.discussion-content,
.reply-content,
.chapter-description,
.resource-description {
  margin-top: 12px;
  color: #606266;
  line-height: 1.75;
  white-space: pre-wrap;
}

.resource-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
}

.resource-item:hover {
  background: #f5f7fa;
}

.resource-main {
  flex: 1;
  min-width: 0;
}

.resource-duration,
.empty-tip,
.desc-label {
  color: #909399;
  font-size: 12px;
}

.desc-section {
  margin-top: 16px;
}

.desc-text {
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}

.discussion-editor,
.reply-editor {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.discussion-toolbar {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.reply-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.reply-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
}

.recent-discussion-list {
  display: grid;
  gap: 10px;
}

.recent-discussion-item {
  width: 100%;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid #f0f2f5;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.recent-discussion-item:last-child {
  border-bottom: 0;
}

.recent-discussion-title {
  display: block;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
}

.recent-discussion-meta {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 768px) {
  .section-header,
  .announcement-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

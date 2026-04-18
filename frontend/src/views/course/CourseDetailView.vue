<template>
  <div class="page-container" v-loading="loading">
    <template v-if="course">
      <div class="page-header">
        <div>
          <span class="page-title">{{ course.title }}</span>
          <el-tag :type="statusTagType(course.status)" style="margin-left: 12px">{{ statusLabel(course.status) }}</el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="canEdit" @click="$router.push(`/courses/${course.id}/edit`)">编辑课程</el-button>
          <el-button v-if="canPublish" type="primary" @click="handleLifecycle('publish')">发布课程</el-button>
          <el-button v-if="canUnpublish" @click="handleLifecycle('unpublish')">撤回发布</el-button>
          <el-button v-if="canRestore" @click="handleLifecycle('restore')">恢复为草稿</el-button>
          <el-button v-if="canArchive" @click="handleLifecycle('archive')">归档课程</el-button>
          <el-button v-if="canEnroll" type="primary" @click="handleEnroll">选课</el-button>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-alert v-if="showEnrollHint" type="info" :closable="false" title="当前可查看课程简介，选课后才能访问章节和资源内容。" style="margin-bottom: 16px" />
          <el-card shadow="never" header="课程章节">
            <div v-if="!contentAccessGranted" class="empty-tip">当前尚未开放课程内容。</div>
            <div v-else-if="chapters.length === 0" class="empty-tip">当前课程还没有章节。</div>
            <el-collapse v-else>
              <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :title="`第 ${chapter.orderIndex} 章 · ${chapter.title}`" :name="chapter.id">
                <div v-if="chapter.description" class="chapter-description">{{ chapter.description }}</div>
                <div v-for="resource in resourceMap[chapter.id] ?? []" :key="resource.id" class="resource-item" @click="$router.push(`/courses/${course.id}/learn/${resource.id}`)">
                  <div class="resource-main">
                    <div class="resource-head">
                      <el-icon><component :is="resourceIcon(resource.type)" /></el-icon>
                      <span>{{ resource.title }}</span>
                      <el-tag size="small">{{ resourceTypeLabel(resource.type) }}</el-tag>
                      <el-tag size="small" :type="resource.taggingStatus === 'CONFIRMED' ? 'success' : 'warning'">{{ resource.taggingStatus === 'CONFIRMED' ? '已标注' : '待标注' }}</el-tag>
                    </div>
                    <div v-if="resource.description" class="resource-description">{{ resource.description }}</div>
                    <div v-if="resource.knowledgePoints?.length" class="resource-tags">
                      <el-tag v-for="knowledgePoint in resource.knowledgePoints" :key="`${resource.id}-${knowledgePoint.id}`" size="small" effect="plain">{{ knowledgePoint.name }}</el-tag>
                    </div>
                  </div>
                  <span v-if="resource.duration" class="resource-duration">{{ formatDuration(resource.duration) }}</span>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" header="课程信息">
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
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Paperclip, VideoPlay } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Chapter, Course, Resource } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const course = ref<Course | null>(null)
const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const contentAccessGranted = ref(false)

const canEdit = computed(() => Boolean(course.value) && (userStore.isAdmin || (!userStore.isStudent && course.value?.teacherId === userStore.user?.id)))
const canPublish = computed(() => canEdit.value && course.value?.status === 'DRAFT')
const canUnpublish = computed(() => canEdit.value && course.value?.status === 'PUBLISHED')
const canArchive = computed(() => canEdit.value && course.value?.status !== 'ARCHIVED')
const canRestore = computed(() => canEdit.value && course.value?.status === 'ARCHIVED')
const canEnroll = computed(() => userStore.isStudent && course.value?.status === 'PUBLISHED' && !contentAccessGranted.value)
const showEnrollHint = computed(() => userStore.isStudent && !contentAccessGranted.value && course.value?.status === 'PUBLISHED')

function statusTagType(status: string) { return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info' }
function statusLabel(status: string) { return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status }
function visibilityLabel(currentCourse: Course) { return currentCourse.visibilityType === 'SELECTED_STUDENTS' ? `定向开放${currentCourse.visibleStudentCount ? ` · ${currentCourse.visibleStudentCount}名学生` : ''}` : '全体学生可见' }
function resourceTypeLabel(type: string) { return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type }
function formatDate(value: string) { return new Date(value).toLocaleString('zh-CN') }
function formatDuration(seconds: number) { return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` }
function resourceIcon(type: string) { return { VIDEO: VideoPlay, DOCUMENT: Document, SLIDE: Paperclip }[type] ?? Document }

async function loadCourseSummary() { course.value = await courseApi.getCourse(String(route.params.id)) }

async function loadCurriculum() {
  const id = String(route.params.id)
  try {
    const chapterList = await courseApi.listChapters(id)
    chapters.value = chapterList
    const resourceEntries = await Promise.all(chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const))
    resourceMap.value = Object.fromEntries(resourceEntries)
    contentAccessGranted.value = true
  } catch (_error) {
    chapters.value = []
    resourceMap.value = {}
    contentAccessGranted.value = false
  }
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
  const updated = await actionMap[action].api(String(course.value.id))
  if (updated && typeof updated === 'object') course.value = updated
  else await loadCourseSummary()
  ElMessage.success(`${actionMap[action].text}成功`)
  await loadCurriculum()
}

async function handleEnroll() {
  if (!course.value) return
  await courseApi.enrollCourse(String(course.value.id))
  ElMessage.success('选课成功')
  await loadCurriculum()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCourseSummary()
    await loadCurriculum()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.header-actions,.resource-head,.resource-tags { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.chapter-description,.resource-description { margin-bottom: 10px; color: #606266; line-height: 1.7; }
.resource-item { display: flex; justify-content: space-between; gap: 12px; padding: 12px; border-radius: 8px; cursor: pointer; }
.resource-item:hover { background: #f5f7fa; }
.resource-main { flex: 1; min-width: 0; }
.resource-duration,.empty-tip,.desc-label { color: #909399; font-size: 12px; }
.desc-section { margin-top: 16px; }
.desc-text { color: #606266; line-height: 1.6; white-space: pre-wrap; }
</style>

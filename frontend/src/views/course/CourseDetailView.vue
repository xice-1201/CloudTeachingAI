<template>
  <div class="page-container" v-loading="loading">
    <template v-if="course">
      <div class="page-header">
        <div>
          <span class="page-title">{{ course.title }}</span>
          <el-tag :type="statusTagType(course.status)" style="margin-left: 12px">
            {{ statusLabel(course.status) }}
          </el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="canEdit" @click="$router.push(`/courses/${course.id}/edit`)">
            编辑与编排
          </el-button>
          <el-button v-if="canEdit && course.status === 'DRAFT'" type="primary" @click="handlePublish">
            发布课程
          </el-button>
          <el-button v-if="canEnroll" type="primary" @click="handleEnroll">选课</el-button>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-card shadow="never" header="课程单元">
            <div v-if="chapters.length === 0" class="empty-tip">当前课程还没有单元。</div>
            <el-collapse v-else>
              <el-collapse-item
                v-for="chapter in chapters"
                :key="chapter.id"
                :title="`单元 ${chapter.orderIndex} · ${chapter.title}`"
                :name="chapter.id"
              >
                <div v-if="chapter.description" class="chapter-description">
                  {{ chapter.description }}
                </div>

                <div
                  v-for="resource in resourceMap[chapter.id] ?? []"
                  :key="resource.id"
                  class="resource-item"
                  @click="$router.push(`/courses/${course.id}/learn/${resource.id}`)"
                >
                  <el-icon><component :is="resourceIcon(resource.type)" /></el-icon>
                  <span>{{ resource.title }}</span>
                  <span class="resource-type">{{ resourceTypeLabel(resource.type) }}</span>
                  <span v-if="resource.duration" class="resource-duration">
                    {{ formatDuration(resource.duration) }}
                  </span>
                </div>

                <div v-if="!resourceMap[chapter.id]?.length" class="empty-tip">当前单元还没有资源。</div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" header="课程信息">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="教师">{{ course.teacherName }}</el-descriptions-item>
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
import { ElMessage } from 'element-plus'
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

const canEdit = computed(() => {
  if (!course.value) {
    return false
  }
  return userStore.isAdmin || (!userStore.isStudent && course.value.teacherId === userStore.user?.id)
})

const canEnroll = computed(() => userStore.isStudent && course.value?.status === 'PUBLISHED')

function statusTagType(status: string) {
  return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info'
}

function statusLabel(status: string) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status
}

function resourceTypeLabel(type: string) {
  return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function formatDuration(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function resourceIcon(type: string) {
  return { VIDEO: VideoPlay, DOCUMENT: Document, SLIDE: Paperclip }[type] ?? Document
}

async function handlePublish() {
  await courseApi.publishCourse(String(course.value!.id))
  course.value!.status = 'PUBLISHED'
  ElMessage.success('课程已发布')
}

async function handleEnroll() {
  await courseApi.enrollCourse(String(course.value!.id))
  ElMessage.success('选课成功')
}

onMounted(async () => {
  loading.value = true
  try {
    const id = String(route.params.id)
    const [courseDetail, chapterList] = await Promise.all([
      courseApi.getCourse(id),
      courseApi.listChapters(id),
    ])

    course.value = courseDetail
    chapters.value = chapterList

    const resourceEntries = await Promise.all(
      chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const),
    )
    resourceMap.value = Object.fromEntries(resourceEntries)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}

.chapter-description {
  margin-bottom: 12px;
  color: #606266;
  line-height: 1.7;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
}

.resource-item:hover {
  background: #f5f7fa;
}

.resource-type {
  color: #909399;
  font-size: 12px;
}

.resource-duration {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

.empty-tip {
  color: #909399;
  font-size: 14px;
  padding: 12px 0;
}

.desc-section {
  margin-top: 16px;
}

.desc-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.desc-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>

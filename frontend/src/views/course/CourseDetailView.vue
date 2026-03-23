<template>
  <div class="page-container" v-loading="loading">
    <template v-if="course">
      <div class="page-header">
        <div>
          <span class="page-title">{{ course.title }}</span>
          <el-tag :type="statusTagType(course.status)" style="margin-left: 12px">{{ statusLabel(course.status) }}</el-tag>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button v-if="canEdit" @click="$router.push(`/courses/${course.id}/edit`)">编辑</el-button>
          <el-button v-if="canEdit && course.status === 'DRAFT'" type="primary" @click="handlePublish">发布课程</el-button>
          <el-button v-if="canEnroll" type="primary" @click="handleEnroll">选课</el-button>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-card shadow="never" header="课程章节">
            <div v-if="chapters.length === 0" class="empty-tip">暂无章节</div>
            <el-collapse v-else>
              <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :title="chapter.title" :name="chapter.id">
                <div v-for="resource in resourceMap[chapter.id] ?? []" :key="resource.id" class="resource-item"
                  @click="$router.push(`/courses/${course!.id}/learn/${resource.id}`)">
                  <el-icon><component :is="resourceIcon(resource.type)" /></el-icon>
                  <span>{{ resource.title }}</span>
                  <span v-if="resource.duration" class="resource-duration">{{ formatDuration(resource.duration) }}</span>
                </div>
                <div v-if="!resourceMap[chapter.id]?.length" class="empty-tip">暂无资源</div>
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
            <div style="margin-top: 16px">
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
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Document, Paperclip } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { useUserStore } from '@/store/user'
import type { Course, Chapter, Resource } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const course = ref<Course | null>(null)
const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<string, Resource[]>>({})

const canEdit = computed(() => !userStore.isStudent && course.value?.teacherId === userStore.user?.id || userStore.isAdmin)
const canEnroll = computed(() => userStore.isStudent && course.value?.status === 'PUBLISHED')

function statusTagType(s: string) { return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[s] ?? 'info' }
function statusLabel(s: string) { return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[s] ?? s }
function formatDate(d: string) { return new Date(d).toLocaleDateString('zh-CN') }
function formatDuration(s: number) { return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` }
function resourceIcon(type: string) { return { VIDEO: VideoPlay, DOCUMENT: Document, SLIDE: Paperclip }[type] ?? Document }

async function handlePublish() {
  await courseApi.publishCourse(course.value!.id)
  ElMessage.success('课程已发布')
  course.value!.status = 'PUBLISHED'
}

async function handleEnroll() {
  await courseApi.enrollCourse(course.value!.id)
  ElMessage.success('选课成功')
}

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id as string
    const [c, chs] = await Promise.all([courseApi.getCourse(id), courseApi.listChapters(id)])
    course.value = c
    chapters.value = chs
    const resources = await Promise.all(chs.map((ch) => courseApi.listResources(ch.id)))
    chs.forEach((ch, i) => { resourceMap.value[ch.id] = resources[i] })
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
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
.resource-item:hover { background: #f5f7fa; }
.resource-duration { margin-left: auto; color: #909399; font-size: 12px; }
.empty-tip { color: #909399; font-size: 14px; padding: 12px 0; }
.desc-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.desc-text { font-size: 14px; color: #606266; line-height: 1.6; }
</style>

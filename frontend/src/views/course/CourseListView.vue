<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">课程列表</span>
      <el-button v-if="!userStore.isStudent" type="primary" :icon="Plus" @click="$router.push('/courses/create')">
        创建课程
      </el-button>
    </div>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item v-if="userStore.isStudent" label="视图">
          <el-radio-group v-model="studentView" @change="resetAndFetch">
            <el-radio-button label="enrolled">我的课程</el-radio-button>
            <el-radio-button label="discover">发现课程</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索课程名称" clearable @change="resetAndFetch" />
        </el-form-item>
        <el-form-item v-if="!userStore.isStudent" label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" @change="resetAndFetch">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6" v-for="course in courses" :key="course.id" style="margin-bottom: 20px">
        <el-card shadow="hover" class="course-card" @click="$router.push(`/courses/${course.id}`)">
          <div class="course-cover">
            <img v-if="course.coverImage" :src="course.coverImage" alt="课程封面" />
            <div v-else class="cover-placeholder">
              <el-icon :size="48" color="#c0c4cc"><Reading /></el-icon>
            </div>
          </div>
          <div class="course-info">
            <div class="course-title">{{ course.title }}</div>
            <div class="course-teacher">{{ course.teacherName }}</div>
            <div v-if="!userStore.isStudent" class="course-visibility">
              {{ visibilityLabel(course) }}
            </div>
            <div v-if="userStore.isStudent && studentView === 'enrolled'" class="course-progress">
              <div class="progress-header">
                <span>学习进度</span>
                <span>{{ courseProgressLabel(course.id) }}</span>
              </div>
              <el-progress :percentage="courseProgressPercent(course.id)" :stroke-width="8" />
            </div>
            <div class="course-footer">
              <el-tag :type="statusTagType(course.status)" size="small">{{ statusLabel(course.status) }}</el-tag>
              <span class="course-date">{{ formatDate(course.updatedAt) }}</span>
            </div>
            <el-button
              v-if="userStore.isStudent && studentView === 'discover'"
              type="primary"
              plain
              size="small"
              class="enroll-button"
              @click.stop="$router.push(`/courses/${course.id}`)"
            >
              查看详情
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && courses.length === 0" description="暂无课程" />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 20px; justify-content: flex-end"
      @change="fetchData"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Reading } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import { learnApi } from '@/api/learn'
import { useUserStore } from '@/store/user'
import type { Course, CourseProgress } from '@/types'

const userStore = useUserStore()
const courses = ref<Course[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(12)
const filters = reactive({ keyword: '', status: '' })
const studentView = ref<'enrolled' | 'discover'>('enrolled')
const enrolledCourseIds = ref<number[]>([])
const courseProgressMap = ref<Record<number, CourseProgress>>({})

function statusTagType(status: string) {
  return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info'
}

function statusLabel(status: string) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status
}

function visibilityLabel(course: Course) {
  if (course.visibilityType === 'SELECTED_STUDENTS') {
    return `定向开放${course.visibleStudentCount ? ` · ${course.visibleStudentCount}名学生` : ''}`
  }
  return '全体学生可见'
}

function formatDate(date: string) {
  return new Date(date).toLocaleDateString('zh-CN')
}

function courseProgressPercent(courseId: number) {
  return Math.round((courseProgressMap.value[courseId]?.progress ?? 0) * 100)
}

function courseProgressLabel(courseId: number) {
  const progress = courseProgressMap.value[courseId]
  if (!progress) return '0%'
  return `${courseProgressPercent(courseId)}%`
}

async function loadCourseProgress(courseList: Course[]) {
  if (!userStore.isStudent || studentView.value !== 'enrolled') {
    courseProgressMap.value = {}
    return
  }

  const entries = await Promise.all(
    courseList.map(async (course) => {
      try {
        const progress = await learnApi.getCourseProgress(String(course.id))
        return [course.id, progress] as const
      } catch {
        return [course.id, {
          courseId: course.id,
          progress: 0,
          totalResources: 0,
          completedResources: 0,
        } satisfies CourseProgress] as const
      }
    }),
  )

  courseProgressMap.value = Object.fromEntries(entries)
}

function resetAndFetch() {
  page.value = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    if (userStore.isStudent) {
      const enrolledResponse = await courseApi.listEnrolledCourses({ page: 1, pageSize: 200 })
      enrolledCourseIds.value = enrolledResponse.items.map((course) => course.id)

      if (studentView.value === 'enrolled') {
        const pagedItems = enrolledResponse.items.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
        courses.value = pagedItems
        total.value = enrolledResponse.total
        await loadCourseProgress(pagedItems)
        return
      }

      const discoverResponse = await courseApi.listCourses({ page: 1, pageSize: 200, keyword: filters.keyword })
      const discoverCourses = discoverResponse.items.filter((course) => !enrolledCourseIds.value.includes(course.id))
      courses.value = discoverCourses.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
      total.value = discoverCourses.length
      courseProgressMap.value = {}
      return
    }

    const res = await courseApi.listCourses({ page: page.value, pageSize: pageSize.value, ...filters })
    courses.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.course-card {
  cursor: pointer;
  transition: transform 0.2s;
}
.course-card:hover {
  transform: translateY(-4px);
}
.course-cover {
  height: 140px;
  overflow: hidden;
  border-radius: 4px;
  margin-bottom: 12px;
  background: #f5f7fa;
}
.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.course-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-teacher {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.course-visibility {
  font-size: 12px;
  color: #67c23a;
  margin-bottom: 10px;
}
.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.course-progress {
  margin-bottom: 12px;
}
.progress-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}
.enroll-button {
  margin-top: 12px;
  width: 100%;
}
.course-date {
  font-size: 12px;
  color: #c0c4cc;
}
</style>

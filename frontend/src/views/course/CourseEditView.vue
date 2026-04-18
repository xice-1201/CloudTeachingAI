<template>
  <div class="page-container" v-loading="pageLoading">
    <div class="page-header">
      <div>
        <span class="page-title">{{ isEdit ? '编辑课程' : '创建课程' }}</span>
        <div class="page-subtitle">
          {{ isEdit ? '维护课程基础信息，并继续配置单元和资源。' : '先创建课程基础信息，保存后再继续配置单元和资源。' }}
        </div>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="isEdit ? 10 : 24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>课程信息</span>
              <el-tag v-if="isEdit && courseStatus" :type="courseStatusTag(courseStatus)">
                {{ courseStatusLabel(courseStatus) }}
              </el-tag>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" size="large">
            <el-form-item label="课程名称" prop="title">
              <el-input v-model="form.title" placeholder="请输入课程名称" />
            </el-form-item>

            <el-form-item label="课程描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="5" placeholder="请输入课程描述" />
            </el-form-item>

            <el-form-item label="可见范围" prop="visibilityType">
              <el-radio-group v-model="form.visibilityType">
                <el-radio-button label="PUBLIC">全体学生可见</el-radio-button>
                <el-radio-button label="SELECTED_STUDENTS">指定学生可见</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.visibilityType === 'SELECTED_STUDENTS'" label="指定学生" prop="visibleStudentIds">
              <el-select
                v-model="form.visibleStudentIds"
                multiple
                filterable
                remote
                reserve-keyword
                placeholder="搜索并选择学生"
                style="width: 100%"
                :remote-method="loadStudentOptions"
                :loading="studentLoading"
              >
                <el-option
                  v-for="student in studentOptions"
                  :key="student.id"
                  :label="`${student.username} (${student.email})`"
                  :value="student.id"
                />
              </el-select>
              <div class="field-tip">仅被选中的学生能看到课程简介并接收发布通知。</div>
            </el-form-item>

            <el-form-item label="封面图片">
              <el-upload
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                @change="handleCoverChange"
              >
                <div class="cover-uploader">
                  <img v-if="coverPreviewUrl" :src="coverPreviewUrl" class="cover-preview" />
                  <div v-else class="cover-placeholder">
                    <el-icon :size="32" color="#c0c4cc"><Plus /></el-icon>
                    <span>上传封面</span>
                  </div>
                </div>
              </el-upload>
              <div class="field-tip">支持 JPG、PNG、GIF、WEBP。图片会在保存课程时上传。</div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="savingCourse" @click="handleSubmit">
                {{ isEdit ? '保存课程' : '创建课程' }}
              </el-button>
              <el-button @click="$router.back()">返回</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col v-if="isEdit" :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <span>课程单元</span>
                <div class="card-subtitle">按单元组织课程结构，并在每个单元下添加学习资源。</div>
              </div>
              <el-button type="primary" @click="openCreateChapterDialog">新增单元</el-button>
            </div>
          </template>

          <div v-if="!chapters.length" class="empty-state">
            <el-empty description="还没有课程单元，先新增一个单元开始编排课程。" />
          </div>

          <el-collapse v-else v-model="activeChapterKeys">
            <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="String(chapter.id)">
              <template #title>
                <div class="chapter-header">
                  <div class="chapter-title-wrap">
                    <span class="chapter-order">单元 {{ chapter.orderIndex }}</span>
                    <span class="chapter-title">{{ chapter.title }}</span>
                  </div>
                  <div class="chapter-actions" @click.stop>
                    <el-button link type="primary" @click="openEditChapterDialog(chapter)">编辑</el-button>
                    <el-button link type="primary" @click="openCreateResourceDialog(chapter)">添加资源</el-button>
                    <el-button link type="danger" @click="handleDeleteChapter(chapter)">删除</el-button>
                  </div>
                </div>
              </template>

              <div v-if="chapter.description" class="chapter-description">
                {{ chapter.description }}
              </div>

              <div class="resource-list">
                <div v-for="resource in resourceMap[chapter.id] ?? []" :key="resource.id" class="resource-card">
                  <div class="resource-main">
                    <div class="resource-top">
                      <span class="resource-order">资源 {{ resource.orderIndex }}</span>
                      <el-tag size="small">{{ resourceTypeLabel(resource.type) }}</el-tag>
                    </div>
                    <div class="resource-title">{{ resource.title }}</div>
                    <div class="resource-meta">
                      <span v-if="resource.duration">时长 {{ formatDuration(resource.duration) }}</span>
                      <span v-if="resource.size">大小 {{ formatFileSize(resource.size) }}</span>
                      <el-link :href="resource.url" target="_blank" type="primary">打开资源</el-link>
                    </div>
                  </div>
                  <div class="resource-actions">
                    <el-button link type="primary" @click="openEditResourceDialog(chapter, resource)">编辑</el-button>
                    <el-button link type="danger" @click="handleDeleteResource(resource)">删除</el-button>
                  </div>
                </div>

                <el-empty v-if="!(resourceMap[chapter.id] ?? []).length" description="当前单元还没有资源" />
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="chapterDialog.visible" :title="chapterDialog.isEdit ? '编辑单元' : '新增单元'" width="520px">
      <el-form ref="chapterFormRef" :model="chapterDialog.form" :rules="chapterRules" label-width="90px">
        <el-form-item label="单元名称" prop="title">
          <el-input v-model="chapterDialog.form.title" placeholder="请输入单元名称" />
        </el-form-item>
        <el-form-item label="单元描述">
          <el-input v-model="chapterDialog.form.description" type="textarea" :rows="4" placeholder="请输入单元描述" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="chapterDialog.form.orderIndex" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSubmitting" @click="handleSubmitChapter">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resourceDialog.visible" :title="resourceDialog.isEdit ? '编辑资源' : '新增资源'" width="560px">
      <el-form ref="resourceFormRef" :model="resourceDialog.form" :rules="resourceRules" label-width="90px">
        <el-form-item label="资源名称" prop="title">
          <el-input v-model="resourceDialog.form.title" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="资源类型" prop="type">
          <el-select v-model="resourceDialog.form.type" placeholder="请选择资源类型" style="width: 100%">
            <el-option label="视频" value="VIDEO" />
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="课件" value="SLIDE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源地址" prop="url">
          <el-input v-model="resourceDialog.form.url" placeholder="请输入资源 URL" />
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="resourceDialog.form.duration" :min="0" :max="86400" />
        </el-form-item>
        <el-form-item label="大小(B)">
          <el-input-number v-model="resourceDialog.form.size" :min="0" :max="2147483647" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="resourceDialog.form.orderIndex" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resourceDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resourceSubmitting" @click="handleSubmitResource">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/course'
import { userApi } from '@/api/user'
import type { Chapter, Course, Resource, User } from '@/types'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const chapterFormRef = ref<FormInstance>()
const resourceFormRef = ref<FormInstance>()

const pageLoading = ref(false)
const savingCourse = ref(false)
const chapterSubmitting = ref(false)
const resourceSubmitting = ref(false)
const studentLoading = ref(false)

const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const activeChapterKeys = ref<string[]>([])
const courseStatus = ref<Course['status'] | ''>('')
const coverPreviewUrl = ref('')
const selectedCoverFile = ref<File | null>(null)
const studentOptions = ref<User[]>([])
let temporaryCoverUrl = ''

const isEdit = computed(() => Boolean(route.params.id))
const courseId = computed(() => String(route.params.id ?? ''))

const form = reactive({
  title: '',
  description: '',
  coverImage: '',
  visibilityType: 'PUBLIC' as Course['visibilityType'],
  visibleStudentIds: [] as number[],
})

const chapterDialog = reactive({
  visible: false,
  isEdit: false,
  chapterId: '' as string,
  form: {
    title: '',
    description: '',
    orderIndex: 1,
  },
})

const resourceDialog = reactive({
  visible: false,
  isEdit: false,
  chapterId: '' as string,
  resourceId: '' as string,
  form: {
    title: '',
    type: 'DOCUMENT' as Resource['type'],
    url: '',
    duration: 0,
    size: 0,
    orderIndex: 1,
  },
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入课程描述', trigger: 'blur' }],
  visibilityType: [{ required: true, message: '请选择课程可见范围', trigger: 'change' }],
  visibleStudentIds: [{
    validator: (_rule, value, callback) => {
      if (form.visibilityType === 'SELECTED_STUDENTS' && (!value || value.length === 0)) {
        callback(new Error('请至少选择一名学生'))
        return
      }
      callback()
    },
    trigger: 'change',
  }],
}

const chapterRules: FormRules = {
  title: [{ required: true, message: '请输入单元名称', trigger: 'blur' }],
}

const resourceRules: FormRules = {
  title: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
  url: [{ required: true, message: '请输入资源地址', trigger: 'blur' }],
}

function setCoverPreview(url: string) {
  if (temporaryCoverUrl && temporaryCoverUrl !== url) {
    URL.revokeObjectURL(temporaryCoverUrl)
    temporaryCoverUrl = ''
  }
  coverPreviewUrl.value = url
}

function handleCoverChange(file: UploadFile) {
  if (!file.raw) return
  selectedCoverFile.value = file.raw
  temporaryCoverUrl = URL.createObjectURL(file.raw)
  coverPreviewUrl.value = temporaryCoverUrl
}

function courseStatusTag(status: Course['status']) {
  return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info'
}

function courseStatusLabel(status: Course['status']) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status
}

function resourceTypeLabel(type: Resource['type']) {
  return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type
}

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}:${String(remainingSeconds).padStart(2, '0')}`
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size} B`
}

function mergeStudentOptions(students: User[]) {
  const merged = new Map<number, User>()
  for (const student of [...studentOptions.value, ...students]) {
    merged.set(student.id, student)
  }
  studentOptions.value = Array.from(merged.values())
}

async function loadStudentOptions(keyword = '') {
  studentLoading.value = true
  try {
    const response = await userApi.listStudents({ page: 1, pageSize: 50, keyword })
    mergeStudentOptions(response.items)
  } finally {
    studentLoading.value = false
  }
}

async function ensureSelectedStudentsLoaded(studentIds: number[]) {
  const missingIds = studentIds.filter((studentId) => !studentOptions.value.some((student) => student.id === studentId))
  if (!missingIds.length) return
  const users = await Promise.all(missingIds.map((studentId) => userApi.getUserById(String(studentId))))
  mergeStudentOptions(users)
}

function resetChapterDialog() {
  chapterDialog.visible = false
  chapterDialog.isEdit = false
  chapterDialog.chapterId = ''
  chapterDialog.form.title = ''
  chapterDialog.form.description = ''
  chapterDialog.form.orderIndex = chapters.value.length + 1
}

function resetResourceDialog(chapterId = '') {
  resourceDialog.visible = false
  resourceDialog.isEdit = false
  resourceDialog.chapterId = chapterId
  resourceDialog.resourceId = ''
  resourceDialog.form.title = ''
  resourceDialog.form.type = 'DOCUMENT'
  resourceDialog.form.url = ''
  resourceDialog.form.duration = 0
  resourceDialog.form.size = 0
  resourceDialog.form.orderIndex = (resourceMap.value[Number(chapterId)]?.length ?? 0) + 1
}

async function loadCourse() {
  if (!isEdit.value) return

  const course = await courseApi.getCourse(courseId.value)
  form.title = course.title
  form.description = course.description
  form.coverImage = course.coverImage ?? ''
  form.visibilityType = course.visibilityType ?? 'PUBLIC'
  form.visibleStudentIds = [...(course.visibleStudentIds ?? [])]
  setCoverPreview(form.coverImage)
  courseStatus.value = course.status

  if (form.visibleStudentIds.length) {
    await ensureSelectedStudentsLoaded(form.visibleStudentIds)
  }
}

async function loadCurriculum() {
  if (!isEdit.value) return

  const chapterList = await courseApi.listChapters(courseId.value)
  chapters.value = chapterList
  activeChapterKeys.value = chapterList.map((chapter) => String(chapter.id))

  const resourceEntries = await Promise.all(
    chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const),
  )
  resourceMap.value = Object.fromEntries(resourceEntries)
}

async function uploadCoverIfNeeded() {
  if (!selectedCoverFile.value) return form.coverImage

  const uploaded = await courseApi.uploadCourseCover(selectedCoverFile.value)
  selectedCoverFile.value = null
  form.coverImage = uploaded.url
  setCoverPreview(uploaded.url)
  return uploaded.url
}

async function handleSubmit() {
  await formRef.value?.validate()
  savingCourse.value = true
  try {
    const coverImage = await uploadCoverIfNeeded()
    const payload = {
      title: form.title,
      description: form.description,
      coverImage,
      visibilityType: form.visibilityType,
      visibleStudentIds: form.visibilityType === 'SELECTED_STUDENTS' ? form.visibleStudentIds : [],
    }

    if (isEdit.value) {
      const course = await courseApi.updateCourse(courseId.value, payload)
      courseStatus.value = course.status
      form.coverImage = course.coverImage ?? ''
      form.visibilityType = course.visibilityType
      form.visibleStudentIds = [...(course.visibleStudentIds ?? [])]
      setCoverPreview(form.coverImage)
      ElMessage.success('课程已保存')
      return
    }

    const course = await courseApi.createCourse(payload)
    form.coverImage = course.coverImage ?? coverImage
    setCoverPreview(form.coverImage)
    ElMessage.success('课程已创建，请继续配置单元和资源')
    await router.replace(`/courses/${course.id}/edit`)
    courseStatus.value = course.status
    await loadCourse()
    await loadCurriculum()
  } finally {
    savingCourse.value = false
  }
}

function openCreateChapterDialog() {
  resetChapterDialog()
  chapterDialog.visible = true
}

function openEditChapterDialog(chapter: Chapter) {
  chapterDialog.visible = true
  chapterDialog.isEdit = true
  chapterDialog.chapterId = String(chapter.id)
  chapterDialog.form.title = chapter.title
  chapterDialog.form.description = chapter.description ?? ''
  chapterDialog.form.orderIndex = chapter.orderIndex
}

async function handleSubmitChapter() {
  await chapterFormRef.value?.validate()
  chapterSubmitting.value = true
  try {
    if (chapterDialog.isEdit) {
      await courseApi.updateChapter(courseId.value, chapterDialog.chapterId, chapterDialog.form)
      ElMessage.success('单元已更新')
    } else {
      await courseApi.createChapter(courseId.value, chapterDialog.form)
      ElMessage.success('单元已创建')
    }
    chapterDialog.visible = false
    await loadCurriculum()
  } finally {
    chapterSubmitting.value = false
  }
}

async function handleDeleteChapter(chapter: Chapter) {
  await ElMessageBox.confirm(`确定删除单元“${chapter.title}”吗？其下资源也会一并删除。`, '删除单元', {
    type: 'warning',
  })
  await courseApi.deleteChapter(courseId.value, String(chapter.id))
  ElMessage.success('单元已删除')
  await loadCurriculum()
}

function openCreateResourceDialog(chapter: Chapter) {
  resetResourceDialog(String(chapter.id))
  resourceDialog.visible = true
}

function openEditResourceDialog(chapter: Chapter, resource: Resource) {
  resourceDialog.visible = true
  resourceDialog.isEdit = true
  resourceDialog.chapterId = String(chapter.id)
  resourceDialog.resourceId = String(resource.id)
  resourceDialog.form.title = resource.title
  resourceDialog.form.type = resource.type
  resourceDialog.form.url = resource.url
  resourceDialog.form.duration = resource.duration ?? 0
  resourceDialog.form.size = resource.size ?? 0
  resourceDialog.form.orderIndex = resource.orderIndex
}

async function handleSubmitResource() {
  await resourceFormRef.value?.validate()
  resourceSubmitting.value = true
  try {
    const payload = {
      ...resourceDialog.form,
      duration: resourceDialog.form.duration || undefined,
      size: resourceDialog.form.size || undefined,
    }

    if (resourceDialog.isEdit) {
      await courseApi.updateResource(resourceDialog.resourceId, payload)
      ElMessage.success('资源已更新')
    } else {
      await courseApi.createResource(resourceDialog.chapterId, payload)
      ElMessage.success('资源已创建')
    }
    resourceDialog.visible = false
    await loadCurriculum()
  } finally {
    resourceSubmitting.value = false
  }
}

async function handleDeleteResource(resource: Resource) {
  await ElMessageBox.confirm(`确定删除资源“${resource.title}”吗？`, '删除资源', {
    type: 'warning',
  })
  await courseApi.deleteResource(String(resource.id))
  ElMessage.success('资源已删除')
  await loadCurriculum()
}

onMounted(async () => {
  pageLoading.value = true
  try {
    await loadStudentOptions()
    await loadCourse()
    await loadCurriculum()
  } finally {
    pageLoading.value = false
  }
})

onBeforeUnmount(() => {
  if (temporaryCoverUrl) {
    URL.revokeObjectURL(temporaryCoverUrl)
    temporaryCoverUrl = ''
  }
})
</script>

<style scoped>
.page-subtitle {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.field-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.cover-uploader {
  width: 220px;
  height: 132px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
}

.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
}

.empty-state {
  padding: 12px 0;
}

.chapter-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 12px;
}

.chapter-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chapter-order {
  color: #909399;
  font-size: 13px;
}

.chapter-title {
  color: #303133;
  font-weight: 600;
}

.chapter-actions {
  flex-shrink: 0;
}

.chapter-description {
  margin-bottom: 16px;
  color: #606266;
  line-height: 1.7;
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafafa;
}

.resource-main {
  min-width: 0;
  flex: 1;
}

.resource-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.resource-order {
  color: #909399;
  font-size: 12px;
}

.resource-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.resource-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}

.resource-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-shrink: 0;
}
</style>

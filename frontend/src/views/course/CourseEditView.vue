<template>
  <div class="page-container" v-loading="pageLoading">
    <div class="page-header">
      <div>
        <span class="page-title">{{ isEdit ? '编辑课程' : '创建课程' }}</span>
        <div class="page-subtitle">{{ isEdit ? '维护课程、章节、资源与知识点标签。' : '先创建课程，再继续配置章节和资源。' }}</div>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="isEdit ? 10 : 24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>课程信息</span>
              <el-tag v-if="isEdit && courseStatus" :type="courseStatusTag(courseStatus)">{{ courseStatusLabel(courseStatus) }}</el-tag>
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
              <div class="field-tip">只有被选中的学生可以查看课程简介并收到课程发布通知。</div>
            </el-form-item>
            <el-form-item label="课程封面">
              <el-upload action="#" :auto-upload="false" :show-file-list="false" accept="image/*" @change="handleCoverChange">
                <div class="cover-uploader">
                  <img v-if="coverPreviewUrl" :src="coverPreviewUrl" class="cover-preview" />
                  <div v-else class="cover-placeholder">
                    <el-icon :size="28"><Plus /></el-icon>
                    <span>上传封面</span>
                  </div>
                </div>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingCourse" @click="handleSubmit">{{ isEdit ? '保存课程' : '创建课程' }}</el-button>
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
                <span>课程内容</span>
                <div class="card-subtitle">按章节组织资源，并为每个资源完成知识点标签确认。</div>
              </div>
              <el-button type="primary" @click="openCreateChapterDialog">新增章节</el-button>
            </div>
          </template>

          <section v-if="allResources.length" class="tag-workbench">
            <div class="tag-workbench-main">
              <div class="tag-workbench-title">AI 标注工作台</div>
              <div class="tag-workbench-stats">
                <span>待确认 {{ tagWorkbenchStats.suggested }}</span>
                <span>待标注 {{ tagWorkbenchStats.untagged }}</span>
                <span>已确认 {{ tagWorkbenchStats.confirmed }}</span>
              </div>
            </div>
            <div class="tag-workbench-actions">
              <el-button
                :disabled="!suggestedResources.length"
                type="primary"
                plain
                @click="openFirstSuggestedResource"
              >
                处理待确认
              </el-button>
              <el-button
                :disabled="!retryableResources.length"
                :loading="batchRetryingTags"
                @click="retryAllUntaggedResources"
              >
                批量重新生成
              </el-button>
            </div>
          </section>

          <el-empty v-if="!chapters.length" description="还没有章节，先新增一个章节开始排课。" />

          <el-collapse v-else v-model="activeChapterKeys">
            <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="String(chapter.id)">
              <template #title>
                <div class="chapter-header">
                  <div class="chapter-title-wrap">
                    <span class="chapter-order">第 {{ chapter.orderIndex }} 章</span>
                    <span class="chapter-title">{{ chapter.title }}</span>
                  </div>
                  <div class="chapter-actions" @click.stop>
                    <el-button link type="primary" @click="openEditChapterDialog(chapter)">编辑</el-button>
                    <el-button link type="primary" @click="openCreateResourceDialog(chapter)">添加资源</el-button>
                    <el-button link type="danger" @click="handleDeleteChapter(chapter)">删除</el-button>
                  </div>
                </div>
              </template>

              <div v-if="chapter.description" class="chapter-description">{{ chapter.description }}</div>

              <div class="resource-list">
                <div v-for="resource in resourceMap[chapter.id] ?? []" :key="resource.id" class="resource-card">
                  <div class="resource-main">
                    <div class="resource-top">
                      <span class="resource-order">资源 {{ resource.orderIndex }}</span>
                      <el-tag size="small">{{ resourceTypeLabel(resource.type) }}</el-tag>
                      <el-tag size="small" :type="resourceTaggingType(resource.taggingStatus)">{{ resourceTaggingLabel(resource.taggingStatus) }}</el-tag>
                    </div>
                    <div class="resource-title">{{ resource.title }}</div>
                    <div v-if="resource.description" class="resource-description">{{ resource.description }}</div>
                    <div class="resource-meta">
                      <span v-if="resource.duration">时长 {{ formatDuration(resource.duration) }}</span>
                      <span v-if="resource.size">大小 {{ formatFileSize(resource.size) }}</span>
                      <el-button link type="primary" @click="previewResource(resource)">预览资源</el-button>
                      <el-button link type="primary" @click="downloadManagedResource(resource)">下载资源</el-button>
                      <span class="resource-source">{{ resource.managedFile ? '平台托管' : '外部链接' }}</span>
                    </div>
                    <div v-if="resource.tags?.length || resource.knowledgePoints?.length" class="resource-tags">
                      <el-tag v-for="tag in (resource.tags?.length ? resource.tags : resource.knowledgePoints)" :key="`${resource.id}-${resourceTagKey(tag)}`" size="small" effect="plain">
                        {{ resourceTagLabel(tag) }}
                      </el-tag>
                    </div>
                  </div>
                  <div class="resource-actions">
                    <el-button v-if="resource.tags?.length || resource.knowledgePoints?.length" link type="primary" @click="openResourceTagReviewDialog(resource)">
                      {{ resource.taggingStatus === 'SUGGESTED' ? '审核AI标注' : '调整标签' }}
                    </el-button>
                    <el-button
                      v-if="canRetryResourceTagging(resource)"
                      link
                      type="primary"
                      :loading="retryingResourceIds.has(resource.id)"
                      @click="retryResourceTagging(resource)"
                    >
                      重新生成标签
                    </el-button>
                    <el-button link type="primary" @click="openEditResourceDialog(chapter, resource)">编辑</el-button>
                    <el-button link type="danger" @click="handleDeleteResource(resource)">删除</el-button>
                  </div>
                </div>
                <el-empty v-if="!(resourceMap[chapter.id] ?? []).length" description="当前章节还没有资源" />
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="chapterDialog.visible" :title="chapterDialog.isEdit ? '编辑章节' : '新增章节'" width="520px">
      <el-form ref="chapterFormRef" :model="chapterDialog.form" :rules="chapterRules" label-width="90px">
        <el-form-item label="章节名称" prop="title"><el-input v-model="chapterDialog.form.title" placeholder="请输入章节名称" /></el-form-item>
        <el-form-item label="章节描述"><el-input v-model="chapterDialog.form.description" type="textarea" :rows="4" placeholder="请输入章节描述" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="chapterDialog.form.orderIndex" :min="1" :max="999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSubmitting" @click="handleSubmitChapter">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resourceDialog.visible" :title="resourceDialog.isEdit ? '编辑资源' : '新增资源'" width="840px">
      <el-form ref="resourceFormRef" :model="resourceDialog.form" :rules="resourceRules" label-width="95px">
        <el-form-item label="本地文件">
          <el-upload action="#" :auto-upload="false" :show-file-list="false" @change="handleResourceFileChange">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
          <div v-if="selectedResourceFile" class="upload-file-row">
            <span>已选择文件：{{ selectedResourceFile.name }}（{{ formatFileSize(selectedResourceFile.size) }}）</span>
            <el-button link type="danger" :disabled="resourceSubmitting" @click="clearSelectedResourceFile">移除</el-button>
          </div>
          <div class="field-tip">
            <template v-if="selectedResourceFile">保存资源时会先上传文件，上传完成后自动写入托管资源地址。</template>
            <template v-else-if="resourceDialog.form.managedFile">当前资源使用已上传文件，可重新选择替换。</template>
            <template v-else>也可以直接填写外部资源地址。</template>
          </div>
          <div v-if="resourceUploadProgress.visible" class="upload-progress">
            <div class="upload-progress-meta">
              <span>{{ resourceUploadProgress.label }}</span>
              <span>{{ resourceUploadProgress.percent }}%</span>
            </div>
            <el-progress :percentage="resourceUploadProgress.percent" :stroke-width="8" />
          </div>
        </el-form-item>
        <el-form-item label="资源名称" prop="title"><el-input v-model="resourceDialog.form.title" placeholder="请输入资源名称" /></el-form-item>
        <el-form-item label="资源描述"><el-input v-model="resourceDialog.form.description" type="textarea" :rows="3" placeholder="请输入资源简介" /></el-form-item>
        <el-form-item label="资源类型" prop="type">
          <el-select v-model="resourceDialog.form.type" style="width: 100%">
            <el-option label="视频" value="VIDEO" />
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="课件" value="SLIDE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源地址" prop="url"><el-input v-model="resourceDialog.form.url" placeholder="请输入资源 URL" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="时长(秒)"><el-input-number v-model="resourceDialog.form.duration" :min="0" :max="86400" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="大小(B)"><el-input-number v-model="resourceDialog.form.size" :min="0" :max="2147483647" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="排序"><el-input-number v-model="resourceDialog.form.orderIndex" :min="1" :max="999" /></el-form-item>
        <el-divider content-position="left">资源标签</el-divider>
        <el-form-item label="手动标签">
          <div class="panel-block">
            <el-select
              v-model="resourceDialog.form.tagLabels"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入或选择资源标签"
              style="width: 100%"
            />
            <div class="field-tip">标签会优先复用系统已有标签，数量不足时 AI 会补充生成新标签。</div>
          </div>
        </el-form-item>
        <el-form-item label="AI建议">
          <div class="panel-block">
            <div class="tree-toolbar">
              <el-button :loading="suggestionLoading" @click="loadTagSuggestions">生成建议标签</el-button>
              <el-button v-if="suggestions.length" type="primary" plain @click="applySuggestedTags(suggestions.map((item) => item.label))">一键接受建议</el-button>
            </div>
            <div v-if="suggestions.length" class="suggestion-list">
              <div v-for="suggestion in suggestions" :key="`${suggestion.kind}-${suggestion.label}`" class="suggestion-item">
                <div>
                  <div class="suggestion-title">
                    {{ suggestion.label }}
                    <el-tag size="small" effect="plain">{{ suggestion.kind === 'GENERATED' ? 'AI 新生成' : '复用现有标签' }}</el-tag>
                  </div>
                  <div v-if="suggestion.path" class="suggestion-path">{{ suggestion.path }}</div>
                  <div class="suggestion-path">{{ suggestion.reason }}</div>
                </div>
                <el-button link type="primary" @click="applySuggestedTags([suggestion.label])">采用</el-button>
              </div>
            </div>
            <div v-else class="field-tip">上传文件、填写资源地址，或至少补充标题/描述后再生成 AI 建议。</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resourceDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resourceSubmitting" @click="handleSubmitResource">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagReviewDialog.visible" title="审核资源标签" width="720px">
      <div v-if="tagReviewDialog.resource" class="tag-review">
        <div class="tag-review-title">
          <span>{{ tagReviewDialog.resource.title }}</span>
          <el-tag size="small" :type="resourceTaggingType(tagReviewDialog.resource.taggingStatus)">
            {{ resourceTaggingLabel(tagReviewDialog.resource.taggingStatus) }}
          </el-tag>
        </div>
        <div class="field-tip">保留需要采用的 AI 推荐标签，也可以取消勾选后仅确认剩余标签。</div>

        <el-empty v-if="!tagReviewDialog.options.length" description="当前资源还没有可确认的标签" />
        <el-checkbox-group v-else v-model="tagReviewDialog.selectedKeys" class="tag-review-options">
          <label v-for="option in tagReviewDialog.options" :key="option.key" class="tag-review-option">
            <el-checkbox :value="option.key">
              <div class="tag-review-option-main">
                <div class="tag-review-option-title">
                  <span>{{ option.label }}</span>
                  <el-tag v-if="option.source" size="small" effect="plain">{{ option.source === 'AI' ? 'AI 推荐' : '手动' }}</el-tag>
                  <el-tag v-if="option.confidence !== undefined" size="small" effect="plain">{{ Math.round(option.confidence * 100) }}%</el-tag>
                </div>
                <div v-if="option.path" class="suggestion-path">{{ option.path }}</div>
              </div>
            </el-checkbox>
          </label>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="tagReviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="tagReviewSubmitting" @click="confirmReviewedResourceTags">确认采用</el-button>
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
import type { Chapter, Course, Resource, ResourceKnowledgePoint, ResourceTag, ResourceTagSuggestion, User } from '@/types'

type RequestLikeError = Error & {
  code?: number
  config?: {
    method?: string
    url?: string
  }
  response?: {
    status?: number
    data?: unknown
  }
  payload?: unknown
}

type TagReviewOption = {
  key: string
  label: string
  knowledgePointId?: number | null
  path?: string | null
  confidence?: number
  source?: ResourceTag['source'] | ResourceKnowledgePoint['source']
}

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
const suggestionLoading = ref(false)
const tagReviewSubmitting = ref(false)
const batchRetryingTags = ref(false)
const retryingResourceIds = ref(new Set<number>())
const selectedResourceFile = ref<File | null>(null)
const resourceUploadProgress = reactive({
  visible: false,
  percent: 0,
  label: '等待上传',
})

const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const activeChapterKeys = ref<string[]>([])
const courseStatus = ref<Course['status'] | ''>('')
const coverPreviewUrl = ref('')
const selectedCoverFile = ref<File | null>(null)
const studentOptions = ref<User[]>([])
const leafKnowledgePointCount = ref(0)
const suggestions = ref<ResourceTagSuggestion[]>([])
let temporaryCoverUrl = ''
let resourceFileSelectionSeq = 0

const isEdit = computed(() => Boolean(route.params.id))
const courseId = computed(() => String(route.params.id ?? ''))
const hasLeafKnowledgePoints = computed(() => leafKnowledgePointCount.value > 0)
const allResources = computed(() => Object.values(resourceMap.value).flat())
const suggestedResources = computed(() => allResources.value.filter((resource) => resource.taggingStatus === 'SUGGESTED'))
const retryableResources = computed(() => allResources.value.filter(canRetryResourceTagging))
const tagWorkbenchStats = computed(() => ({
  suggested: suggestedResources.value.length,
  untagged: allResources.value.filter((resource) => resource.taggingStatus === 'UNTAGGED' || !resource.taggingStatus).length,
  confirmed: allResources.value.filter((resource) => resource.taggingStatus === 'CONFIRMED').length,
}))

const form = reactive({ title: '', description: '', coverImage: '', visibilityType: 'PUBLIC' as Course['visibilityType'], visibleStudentIds: [] as number[] })
const chapterDialog = reactive({ visible: false, isEdit: false, chapterId: '', form: { title: '', description: '', orderIndex: 1 } })
const resourceDialog = reactive({
  visible: false,
  isEdit: false,
  chapterId: '',
  resourceId: '',
  form: { title: '', type: 'DOCUMENT' as Resource['type'], url: '', description: '', managedFile: false, knowledgePointIds: [] as number[], tagLabels: [] as string[], duration: 0, size: 0, orderIndex: 1 },
})
const tagReviewDialog = reactive({
  visible: false,
  resource: null as Resource | null,
  options: [] as TagReviewOption[],
  selectedKeys: [] as string[],
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入课程描述', trigger: 'blur' }],
  visibilityType: [{ required: true, message: '请选择可见范围', trigger: 'change' }],
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

const chapterRules: FormRules = { title: [{ required: true, message: '请输入章节名称', trigger: 'blur' }] }
const resourceRules: FormRules = {
  title: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
  url: [{
    validator: (_rule, value, callback) => {
      if (selectedResourceFile.value) return callback()
      if (typeof value === 'string' && value.trim()) return callback()
      if (resourceDialog.isEdit && resourceDialog.form.managedFile) return callback()
      callback(new Error('请上传资源文件或填写资源地址'))
    },
    trigger: 'blur',
  }],
  knowledgePointIds: [{
    validator: (_rule, value, callback) => {
      if (!hasLeafKnowledgePoints.value) return callback()
      if (!value || value.length === 0) return callback(new Error('请至少选择一个知识点标签'))
      callback()
    },
    trigger: 'change',
  }],
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

function getFileBaseName(fileName: string) {
  const normalized = fileName.trim()
  const lastDotIndex = normalized.lastIndexOf('.')
  if (lastDotIndex <= 0) return normalized
  return normalized.slice(0, lastDotIndex)
}

function inferResourceTypeFromFile(file: File): Resource['type'] {
  const fileName = file.name.toLowerCase()
  const mimeType = file.type.toLowerCase()
  if (mimeType.startsWith('video/') || /\.(mp4|mov|m4v|webm|ogv|ogg|mpe|mpeg|mpg|avi|wmv|mkv|flv|3gp)$/i.test(fileName)) {
    return 'VIDEO'
  }
  if (
    mimeType.includes('presentation')
    || mimeType.includes('powerpoint')
    || /\.(ppt|pptx|pps|ppsx|odp|key)$/i.test(fileName)
  ) {
    return 'SLIDE'
  }
  return 'DOCUMENT'
}

function analyzeVideoDuration(file: File, selectionSeq: number) {
  const videoUrl = URL.createObjectURL(file)
  const video = document.createElement('video')
  video.preload = 'metadata'

  const cleanup = () => {
    URL.revokeObjectURL(videoUrl)
    video.removeAttribute('src')
    video.load()
  }

  video.onloadedmetadata = () => {
    if (selectionSeq === resourceFileSelectionSeq && selectedResourceFile.value === file && Number.isFinite(video.duration)) {
      resourceDialog.form.duration = Math.round(video.duration)
    }
    cleanup()
  }

  video.onerror = () => {
    cleanup()
  }

  video.src = videoUrl
}

function handleResourceFileChange(file: UploadFile) {
  if (!file.raw) return
  const selectionSeq = ++resourceFileSelectionSeq
  selectedResourceFile.value = file.raw
  resourceUploadProgress.visible = false
  resourceUploadProgress.percent = 0
  resourceUploadProgress.label = '等待上传'
  resourceDialog.form.size = file.raw.size
  if (!resourceDialog.form.title.trim()) {
    resourceDialog.form.title = getFileBaseName(file.raw.name)
  }
  const inferredType = inferResourceTypeFromFile(file.raw)
  resourceDialog.form.type = inferredType
  if (inferredType === 'VIDEO') {
    analyzeVideoDuration(file.raw, selectionSeq)
  }
  suggestions.value = []
}

function clearSelectedResourceFile() {
  resourceFileSelectionSeq += 1
  selectedResourceFile.value = null
  resourceUploadProgress.visible = false
  resourceUploadProgress.percent = 0
  resourceUploadProgress.label = '等待上传'
}

function logCourseEditError(step: string, error: unknown, extra?: Record<string, unknown>) {
  const requestError = error as RequestLikeError
  console.group(`[CourseEditView] ${step} failed`)
  if (extra) {
    console.error('Context:', extra)
  }
  console.error('Request summary:', {
    step,
    code: requestError?.code,
    method: requestError?.config?.method?.toUpperCase?.(),
    url: requestError?.config?.url,
    status: requestError?.response?.status,
  })
  if (requestError?.response?.data !== undefined) {
    console.error('Response data:', requestError.response.data)
  } else if (requestError?.payload !== undefined) {
    console.error('Response payload:', requestError.payload)
  }
  console.error('Error object:', error)
  if (error instanceof Error && error.stack) {
    console.error('Stack trace:\n' + error.stack)
  }
  console.trace(`[CourseEditView] ${step} trace`)
  console.groupEnd()
}

function courseStatusTag(status: Course['status']) { return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info' }
function courseStatusLabel(status: Course['status']) { return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status }
function resourceTypeLabel(type: Resource['type']) { return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type }
function resourceTaggingLabel(status?: Resource['taggingStatus']) { return { CONFIRMED: '已确认', SUGGESTED: '待确认', UNTAGGED: '待标注', PROCESSING: '生成中', FAILED: '生成失败' }[status ?? 'UNTAGGED'] ?? '待标注' }
function resourceTaggingType(status?: Resource['taggingStatus']) { return { CONFIRMED: 'success', SUGGESTED: 'warning', UNTAGGED: 'info', PROCESSING: 'primary', FAILED: 'danger' }[status ?? 'UNTAGGED'] ?? 'info' }
function formatDuration(seconds: number) { const minutes = Math.floor(seconds / 60); const remainingSeconds = seconds % 60; return `${minutes}:${String(remainingSeconds).padStart(2, '0')}` }
function formatFileSize(size: number) { if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`; if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`; return `${size} B` }
function resourceTagLabel(tag: ResourceTag | ResourceKnowledgePoint) { return 'label' in tag ? tag.label : tag.name }
function resourceTagKey(tag: ResourceTag | ResourceKnowledgePoint) { return 'label' in tag ? tag.label : tag.id }
function resourceTagPath(tag: ResourceTag | ResourceKnowledgePoint) { return 'label' in tag ? tag.knowledgePointPath : tag.path }
function resourceTagKnowledgePointId(tag: ResourceTag | ResourceKnowledgePoint) { return 'label' in tag ? tag.knowledgePointId : tag.id }
function resolveResourceUrl(url: string) { if (/^https?:\/\//i.test(url) || url.startsWith('/')) return url; return `/${url}` }
function normalizeTagLabels(labels: string[]) {
  const normalized = new Map<string, string>()
  labels.forEach((label) => {
    const value = label.trim()
    if (!value) return
    normalized.set(value.toLowerCase(), value)
  })
  return Array.from(normalized.values())
}
function applySuggestedTags(labels: string[]) { resourceDialog.form.tagLabels = normalizeTagLabels([...resourceDialog.form.tagLabels, ...labels]) }
function buildTagReviewOptions(resource: Resource) {
  const options = new Map<string, TagReviewOption>()
  const tags = [
    ...(resource.tags ?? []),
    ...(resource.knowledgePoints ?? []),
  ]

  for (const tag of tags) {
    const label = resourceTagLabel(tag)
    const knowledgePointId = resourceTagKnowledgePointId(tag)
    const key = knowledgePointId ? `kp:${knowledgePointId}` : `label:${label.trim().toLowerCase()}`
    if (!label || options.has(key)) continue
    options.set(key, {
      key,
      label,
      knowledgePointId,
      path: resourceTagPath(tag),
      confidence: tag.confidence,
      source: tag.source,
    })
  }
  return Array.from(options.values())
}
function replaceResourceInMap(updatedResource: Resource) {
  const nextResourceMap = { ...resourceMap.value }
  const chapterResources = nextResourceMap[updatedResource.chapterId] ?? []
  nextResourceMap[updatedResource.chapterId] = chapterResources.map((resource) => (
    resource.id === updatedResource.id ? updatedResource : resource
  ))
  resourceMap.value = nextResourceMap
}
function canRetryResourceTagging(resource: Resource) {
  return resource.taggingStatus === 'UNTAGGED' || resource.taggingStatus === 'FAILED' || (!resource.tags?.length && !resource.knowledgePoints?.length)
}
function openFirstSuggestedResource() {
  const firstResource = suggestedResources.value[0]
  if (firstResource) openResourceTagReviewDialog(firstResource)
}
function openResourceTagReviewDialog(resource: Resource) {
  const options = buildTagReviewOptions(resource)
  tagReviewDialog.resource = resource
  tagReviewDialog.options = options
  tagReviewDialog.selectedKeys = options.map((option) => option.key)
  tagReviewDialog.visible = true
}
function extractFileName(value?: string) {
  if (!value) return ''
  const withoutQuery = value.split('?')[0] ?? value
  const parts = withoutQuery.split('/')
  return parts[parts.length - 1] ?? withoutQuery
}
function mergeStudentOptions(students: User[]) {
  const merged = new Map<number, User>()
  for (const student of [...studentOptions.value, ...students]) merged.set(student.id, student)
  studentOptions.value = Array.from(merged.values())
}

function previewResource(resource: Resource) {
  window.open(resolveResourceUrl(resource.url), '_blank', 'noopener')
}

async function downloadManagedResource(resource: Resource) {
  if (!resource.managedFile) {
    window.open(resolveResourceUrl(resource.url), '_blank', 'noopener')
    return
  }

  const token = localStorage.getItem('token')
  const response = await fetch(resolveResourceUrl(`${resource.url}?download=true`), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    ElMessage.error('资源下载失败，请稍后重试')
    return
  }
  const blob = await response.blob()
  const downloadUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = resource.title
  link.click()
  URL.revokeObjectURL(downloadUrl)
}

async function loadStudentOptions(keyword = '') {
  studentLoading.value = true
  try {
    const response = await userApi.listStudents({ page: 1, pageSize: 50, keyword })
    mergeStudentOptions(response.items)
  } catch (error) {
    logCourseEditError('loadStudentOptions', error, { keyword })
    throw error
  } finally {
    studentLoading.value = false
  }
}

async function ensureSelectedStudentsLoaded(studentIds: number[]) {
  const missingIds = studentIds.filter((studentId) => !studentOptions.value.some((student) => student.id === studentId))
  if (!missingIds.length) return

  const results = await Promise.allSettled(missingIds.map((studentId) => userApi.getUserById(String(studentId))))
  const users = results
    .filter((result): result is PromiseFulfilledResult<User> => result.status === 'fulfilled')
    .map((result) => result.value)

  if (users.length) {
    mergeStudentOptions(users)
  }

  const failedIds = missingIds.filter((_, index) => results[index]?.status === 'rejected')
  if (failedIds.length) {
    results.forEach((result, index) => {
      if (result.status === 'rejected') {
        logCourseEditError('ensureSelectedStudentsLoaded', result.reason, {
          missingStudentId: missingIds[index],
        })
      }
    })
    ElMessage.warning(`部分指定学生信息加载失败，已忽略 ${failedIds.length} 条失效学生记录`)
  }
}

async function loadTagSuggestions() {
  suggestionLoading.value = true
  try {
    const sourceUrl = resourceDialog.form.managedFile ? undefined : (resourceDialog.form.url?.trim() || undefined)
    const fileName = selectedResourceFile.value?.name || extractFileName(sourceUrl)
    const hasPreviewInput = Boolean(
      selectedResourceFile.value
      || (resourceDialog.isEdit && resourceDialog.form.managedFile)
      || sourceUrl
      || resourceDialog.form.title?.trim()
      || resourceDialog.form.description?.trim(),
    )
    if (!hasPreviewInput) {
      ElMessage.warning('请先上传文件、填写资源地址，或至少补充标题/描述后再生成 AI 建议')
      suggestions.value = []
      return
    }
    console.info('[CourseEditView] loadTagSuggestions request', {
      isEdit: resourceDialog.isEdit,
      managedFile: resourceDialog.form.managedFile,
      resourceId: resourceDialog.resourceId,
      type: resourceDialog.form.type,
      titlePresent: Boolean(resourceDialog.form.title?.trim()),
      descriptionPresent: Boolean(resourceDialog.form.description?.trim()),
      sourceUrlPresent: Boolean(sourceUrl),
      localFilePresent: Boolean(selectedResourceFile.value),
      localFileName: selectedResourceFile.value?.name,
      localFileSize: selectedResourceFile.value?.size,
      fileName,
    })
    suggestions.value = resourceDialog.isEdit && resourceDialog.form.managedFile && !selectedResourceFile.value
      ? await courseApi.getResourceTagSuggestions(resourceDialog.resourceId)
      : await courseApi.previewResourceTagSuggestions({
          title: resourceDialog.form.title,
          description: resourceDialog.form.description,
          type: resourceDialog.form.type,
          sourceUrl,
          fileName,
          file: selectedResourceFile.value ?? undefined,
        })
    if (!suggestions.value.length) {
      if (resourceDialog.form.type === 'VIDEO') {
        ElMessage.warning('未成功获取视频转写，暂时无法基于内容生成标签，请检查视频音轨与转写配置')
      } else {
        ElMessage.info('没有生成建议标签，请手动补充标签')
      }
    }
  } finally {
    suggestionLoading.value = false
  }
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
  selectedResourceFile.value = null
  resourceUploadProgress.visible = false
  resourceUploadProgress.percent = 0
  resourceUploadProgress.label = '等待上传'
  suggestions.value = []
  resourceDialog.form.title = ''
  resourceDialog.form.type = 'DOCUMENT'
  resourceDialog.form.url = ''
  resourceDialog.form.description = ''
  resourceDialog.form.managedFile = false
  resourceDialog.form.knowledgePointIds = []
  resourceDialog.form.tagLabels = []
  resourceDialog.form.duration = 0
  resourceDialog.form.size = 0
  resourceDialog.form.orderIndex = (resourceMap.value[Number(chapterId)]?.length ?? 0) + 1
}

async function loadCourse() {
  if (!isEdit.value) return
  try {
    const course = await courseApi.getCourse(courseId.value)
    form.title = course.title
    form.description = course.description
    form.coverImage = course.coverImage ?? ''
    form.visibilityType = course.visibilityType ?? 'PUBLIC'
    form.visibleStudentIds = [...(course.visibleStudentIds ?? [])]
    setCoverPreview(form.coverImage)
    courseStatus.value = course.status
    if (form.visibleStudentIds.length) await ensureSelectedStudentsLoaded(form.visibleStudentIds)
  } catch (error) {
    logCourseEditError('loadCourse', error, { courseId: courseId.value })
    throw error
  }
}

async function loadCurriculum() {
  if (!isEdit.value) return
  try {
    const chapterList = await courseApi.listChapters(courseId.value)
    chapters.value = chapterList
    activeChapterKeys.value = chapterList.map((chapter) => String(chapter.id))
    const resourceResults = await Promise.allSettled(
      chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const),
    )

    const nextResourceMap: Record<number, Resource[]> = {}
    let failedChapterCount = 0

    resourceResults.forEach((result, index) => {
      const chapter = chapterList[index]
      if (!chapter) return

      if (result.status === 'fulfilled') {
        const [chapterId, resources] = result.value
        nextResourceMap[chapterId] = resources
        return
      }

      failedChapterCount += 1
      nextResourceMap[chapter.id] = []
      logCourseEditError('loadCurriculum.resources', result.reason, {
        courseId: courseId.value,
        chapterId: chapter.id,
      })
    })

    resourceMap.value = nextResourceMap
    if (failedChapterCount > 0) {
      ElMessage.warning(`有 ${failedChapterCount} 个章节的资源加载失败，页面已继续展示其余内容`)
    }
  } catch (error) {
    logCourseEditError('loadCurriculum', error, { courseId: courseId.value })
    throw error
  }
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
    const payload = { title: form.title, description: form.description, coverImage, visibilityType: form.visibilityType, visibleStudentIds: form.visibilityType === 'SELECTED_STUDENTS' ? form.visibleStudentIds : [] }
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
    ElMessage.success('课程已创建，请继续配置章节和资源')
    await router.replace(`/courses/${course.id}/edit`)
    courseStatus.value = course.status
    await loadCourse()
    await loadCurriculum()
  } finally {
    savingCourse.value = false
  }
}

function openCreateChapterDialog() { resetChapterDialog(); chapterDialog.visible = true }
function openEditChapterDialog(chapter: Chapter) { chapterDialog.visible = true; chapterDialog.isEdit = true; chapterDialog.chapterId = String(chapter.id); chapterDialog.form.title = chapter.title; chapterDialog.form.description = chapter.description ?? ''; chapterDialog.form.orderIndex = chapter.orderIndex }

async function handleSubmitChapter() {
  await chapterFormRef.value?.validate()
  chapterSubmitting.value = true
  try {
    if (chapterDialog.isEdit) {
      await courseApi.updateChapter(courseId.value, chapterDialog.chapterId, chapterDialog.form)
      ElMessage.success('章节已更新')
    } else {
      await courseApi.createChapter(courseId.value, chapterDialog.form)
      ElMessage.success('章节已创建')
    }
    chapterDialog.visible = false
    await loadCurriculum()
  } finally {
    chapterSubmitting.value = false
  }
}

async function handleDeleteChapter(chapter: Chapter) {
  await ElMessageBox.confirm(`确定删除章节“${chapter.title}”吗？其下资源会一并删除。`, '删除章节', { type: 'warning' })
  await courseApi.deleteChapter(courseId.value, String(chapter.id))
  ElMessage.success('章节已删除')
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
  selectedResourceFile.value = null
  resourceUploadProgress.visible = false
  resourceUploadProgress.percent = 0
  resourceUploadProgress.label = '等待上传'
  suggestions.value = []
  resourceDialog.form.title = resource.title
  resourceDialog.form.type = resource.type
  resourceDialog.form.url = resource.managedFile ? '' : (resource.sourceUrl ?? resource.url)
  resourceDialog.form.description = resource.description ?? ''
  resourceDialog.form.managedFile = Boolean(resource.managedFile)
  resourceDialog.form.knowledgePointIds = [...(resource.knowledgePoints?.map((item) => item.id) ?? [])]
  resourceDialog.form.tagLabels = normalizeTagLabels([
    ...(resource.tags?.map((item) => item.label) ?? []),
    ...(resource.knowledgePoints?.map((item) => item.name) ?? []),
  ])
  resourceDialog.form.duration = resource.duration ?? 0
  resourceDialog.form.size = resource.size ?? 0
  resourceDialog.form.orderIndex = resource.orderIndex
}

async function handleSubmitResource() {
  await resourceFormRef.value?.validate()
  resourceSubmitting.value = true
  try {
    let resourceUrl = resourceDialog.form.url?.trim() ?? ''
    let resourceSize = resourceDialog.form.size || undefined
    if (selectedResourceFile.value) {
      resourceUploadProgress.visible = true
      resourceUploadProgress.percent = 0
      resourceUploadProgress.label = '正在上传资源文件'
      const uploaded = await courseApi.uploadResourceFile(
        selectedResourceFile.value,
        resourceDialog.form.type,
        (percent) => {
          resourceUploadProgress.percent = percent
        },
      )
      resourceUploadProgress.percent = 100
      resourceUploadProgress.label = '上传完成，正在保存资源信息'
      resourceUrl = uploaded.storageKey
      resourceSize = uploaded.size || resourceSize
    }
    const payload = {
      title: resourceDialog.form.title,
      type: resourceDialog.form.type,
      url: resourceUrl || undefined,
      description: resourceDialog.form.description || undefined,
      tagLabels: normalizeTagLabels(resourceDialog.form.tagLabels),
      duration: resourceDialog.form.duration || undefined,
      size: resourceSize,
      orderIndex: resourceDialog.form.orderIndex,
    }
    if (resourceDialog.isEdit) {
      await courseApi.updateResource(resourceDialog.resourceId, payload)
      ElMessage.success('资源已更新')
    } else {
      await courseApi.createResource(resourceDialog.chapterId, payload)
      ElMessage.success('资源已创建')
    }
    resourceDialog.visible = false
    selectedResourceFile.value = null
    resourceUploadProgress.visible = false
    resourceUploadProgress.percent = 0
    resourceUploadProgress.label = '等待上传'
    await loadCurriculum()
  } finally {
    if (selectedResourceFile.value && resourceUploadProgress.visible && resourceUploadProgress.percent < 100) {
      resourceUploadProgress.label = '上传失败，请检查文件类型、大小或网络后重试'
    }
    resourceSubmitting.value = false
  }
}

async function handleDeleteResource(resource: Resource) {
  await ElMessageBox.confirm(`确定删除资源“${resource.title}”吗？`, '删除资源', { type: 'warning' })
  await courseApi.deleteResource(String(resource.id))
  ElMessage.success('资源已删除')
  await loadCurriculum()
}

function setResourceRetrying(resourceId: number, retrying: boolean) {
  const nextIds = new Set(retryingResourceIds.value)
  if (retrying) {
    nextIds.add(resourceId)
  } else {
    nextIds.delete(resourceId)
  }
  retryingResourceIds.value = nextIds
}

async function retryResourceTagging(resource: Resource, silent = false) {
  setResourceRetrying(resource.id, true)
  try {
    const updatedResource = await courseApi.retryResourceTagging(String(resource.id))
    replaceResourceInMap(updatedResource)
    if (!silent) {
      ElMessage.success('已重新提交 AI 标注任务')
    }
  } finally {
    setResourceRetrying(resource.id, false)
  }
}

async function retryAllUntaggedResources() {
  const resources = retryableResources.value
  if (!resources.length) return
  batchRetryingTags.value = true
  try {
    const results = await Promise.allSettled(resources.map((resource) => retryResourceTagging(resource, true)))
    const failedCount = results.filter((result) => result.status === 'rejected').length
    if (failedCount > 0) {
      ElMessage.warning(`有 ${failedCount} 个资源重新生成失败，请稍后重试`)
    } else {
      ElMessage.success(`已提交 ${resources.length} 个资源的 AI 标注任务`)
    }
  } finally {
    batchRetryingTags.value = false
  }
}

async function confirmReviewedResourceTags() {
  if (!tagReviewDialog.resource) return
  const selectedOptions = tagReviewDialog.options.filter((option) => tagReviewDialog.selectedKeys.includes(option.key))
  tagReviewSubmitting.value = true
  try {
    const knowledgePointIds = selectedOptions
      .map((option) => option.knowledgePointId)
      .filter((id): id is number => typeof id === 'number')
    const tagLabels = selectedOptions
      .filter((option) => !option.knowledgePointId)
      .map((option) => option.label)
    const updatedResource = await courseApi.confirmResourceTags(String(tagReviewDialog.resource.id), {
      knowledgePointIds,
      tagLabels,
    })
    replaceResourceInMap(updatedResource)
    tagReviewDialog.visible = false
    tagReviewDialog.resource = null
    tagReviewDialog.options = []
    tagReviewDialog.selectedKeys = []
    ElMessage.success(selectedOptions.length ? '资源标签已确认' : '资源标签已清空')
  } finally {
    tagReviewSubmitting.value = false
  }
}

onMounted(async () => {
  pageLoading.value = true
  try {
    const [studentResult] = await Promise.allSettled([loadStudentOptions()])

    if (studentResult.status === 'rejected') {
      ElMessage.warning('学生列表加载失败，编辑页已继续加载。请查看浏览器控制台定位原因。')
    }

    await loadCourse()
    await loadCurriculum()
  } catch (error) {
    logCourseEditError('onMounted', error, { courseId: courseId.value, isEdit: isEdit.value })
    ElMessage.error('课程编辑页初始化失败，请查看浏览器控制台中的详细堆栈。')
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
.page-subtitle,.card-subtitle,.field-tip,.knowledge-node-path,.suggestion-path { margin-top: 8px; color: #909399; font-size: 12px; }
.card-header,.chapter-header,.tree-toolbar,.tag-review-title,.tag-review-option-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.cover-uploader { width: 220px; height: 132px; border: 1px dashed #d9d9d9; border-radius: 8px; cursor: pointer; overflow: hidden; }
.cover-preview { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #909399; }
.chapter-title-wrap,.resource-top,.selected-tags,.resource-tags { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.chapter-description,.resource-description { margin-bottom: 12px; color: #606266; line-height: 1.7; }
.resource-list,.suggestion-list { display: flex; flex-direction: column; gap: 12px; }
.resource-card,.suggestion-item { display: flex; justify-content: space-between; gap: 16px; padding: 14px 16px; border: 1px solid #ebeef5; border-radius: 10px; background: #fafafa; }
.resource-main,.panel-block { flex: 1; min-width: 0; width: 100%; }
.resource-title { color: #303133; font-size: 15px; font-weight: 600; }
.resource-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 10px; color: #909399; font-size: 13px; }
.resource-actions { display: flex; align-items: flex-start; gap: 8px; flex-shrink: 0; }
.resource-source { color: #c0c4cc; }
.upload-file-row { display: flex; align-items: center; gap: 12px; margin-top: 8px; color: #606266; font-size: 13px; }
.upload-progress { width: 100%; margin-top: 10px; }
.upload-progress-meta { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 6px; color: #606266; font-size: 12px; }
.knowledge-tree { max-height: 260px; overflow: auto; border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 12px; }
.knowledge-node { display: flex; flex-direction: column; gap: 4px; padding: 2px 0; }
.tag-review { display: grid; gap: 12px; }
.tag-review-title { justify-content: flex-start; color: #303133; font-size: 16px; font-weight: 600; }
.tag-review-options { display: grid; gap: 10px; max-height: 360px; overflow: auto; }
.tag-review-option { display: block; padding: 12px; border: 1px solid #ebeef5; border-radius: 8px; background: #fafafa; cursor: pointer; }
.tag-review-option-main { display: grid; gap: 4px; min-width: 0; }
.tag-review-option-title { justify-content: flex-start; color: #303133; font-weight: 500; }
.tag-workbench { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; margin-bottom: 16px; border: 1px solid #ebeef5; border-radius: 8px; background: #f8fafc; }
.tag-workbench-main { min-width: 0; }
.tag-workbench-title { color: #303133; font-size: 14px; font-weight: 600; }
.tag-workbench-stats,.tag-workbench-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.tag-workbench-stats { margin-top: 6px; color: #909399; font-size: 12px; }
</style>

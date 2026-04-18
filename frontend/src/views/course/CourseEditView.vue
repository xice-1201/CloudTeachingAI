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

          <el-alert
            v-if="!hasLeafKnowledgePoints"
            type="warning"
            :closable="false"
            title="当前没有可用的叶子知识点，请先在管理员后台维护知识点体系。"
            style="margin-bottom: 16px"
          />

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
                      <el-link :href="resource.url" target="_blank" type="primary">打开资源</el-link>
                    </div>
                    <div v-if="resource.knowledgePoints?.length" class="resource-tags">
                      <el-tag v-for="knowledgePoint in resource.knowledgePoints" :key="`${resource.id}-${knowledgePoint.id}`" size="small" effect="plain">
                        {{ knowledgePoint.name }}
                      </el-tag>
                    </div>
                  </div>
                  <div class="resource-actions">
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
        <el-form-item label="资源名称" prop="title"><el-input v-model="resourceDialog.form.title" placeholder="请输入资源名称" /></el-form-item>
        <el-form-item label="资源描述"><el-input v-model="resourceDialog.form.description" type="textarea" :rows="3" placeholder="请输入资源简介" /></el-form-item>
        <el-form-item label="资源类型" prop="type">
          <el-select v-model="resourceDialog.form.type" style="width: 100%">
            <el-option label="视频" value="VIDEO" />
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="课件" value="SLIDE" />
          </el-select>
        </el-form-item>
        <el-form-item label="本地文件">
          <el-upload action="#" :auto-upload="false" :show-file-list="false" @change="handleResourceFileChange">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
          <div class="field-tip">
            <template v-if="selectedResourceFile">已选择文件：{{ selectedResourceFile.name }}</template>
            <template v-else-if="resourceDialog.form.managedFile">当前资源使用已上传文件，可重新选择替换。</template>
            <template v-else>也可以直接填写外部资源地址。</template>
          </div>
        </el-form-item>
        <el-form-item label="资源地址" prop="url"><el-input v-model="resourceDialog.form.url" placeholder="请输入资源 URL" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="时长(秒)"><el-input-number v-model="resourceDialog.form.duration" :min="0" :max="86400" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="大小(B)"><el-input-number v-model="resourceDialog.form.size" :min="0" :max="2147483647" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="排序"><el-input-number v-model="resourceDialog.form.orderIndex" :min="1" :max="999" /></el-form-item>
        <el-divider content-position="left">知识点标签</el-divider>
        <el-form-item label="已选标签" prop="knowledgePointIds">
          <div class="panel-block">
            <div v-if="resourceDialog.form.knowledgePointIds.length" class="selected-tags">
              <el-tag v-for="knowledgePointId in resourceDialog.form.knowledgePointIds" :key="knowledgePointId" closable effect="plain" @close="removeSelectedKnowledgePoint(knowledgePointId)">
                {{ knowledgePointLabel(knowledgePointId) }}
              </el-tag>
            </div>
            <div v-else class="field-tip">还没有选择知识点标签。</div>
          </div>
        </el-form-item>
        <el-form-item label="AI建议">
          <div class="panel-block">
            <div class="tree-toolbar">
              <el-button :loading="suggestionLoading" @click="loadTagSuggestions">生成建议标签</el-button>
              <el-button v-if="suggestions.length" type="primary" plain @click="applySuggestedKnowledgePoints(suggestions.map((item) => item.knowledgePointId))">一键接受建议</el-button>
            </div>
            <div v-if="suggestions.length" class="suggestion-list">
              <div v-for="suggestion in suggestions" :key="suggestion.knowledgePointId" class="suggestion-item">
                <div>
                  <div class="suggestion-title">{{ suggestion.knowledgePointName }}</div>
                  <div class="suggestion-path">{{ suggestion.path }}</div>
                  <div class="suggestion-path">{{ suggestion.reason }}</div>
                </div>
                <el-button link type="primary" @click="applySuggestedKnowledgePoints([suggestion.knowledgePointId])">采用</el-button>
              </div>
            </div>
            <div v-else class="field-tip">可以根据资源标题和简介生成 AI 建议，也可以直接从分类树手动选择。</div>
          </div>
        </el-form-item>
        <el-form-item label="分类树">
          <div class="panel-block">
            <div class="tree-toolbar">
              <el-input v-model="knowledgePointKeyword" clearable placeholder="搜索知识点" />
              <el-button @click="clearKnowledgePointSelection">清空已选</el-button>
            </div>
            <el-tree
              ref="knowledgePointTreeRef"
              class="knowledge-tree"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              :data="knowledgePointTree"
              :props="{ label: 'name', children: 'children' }"
              :filter-node-method="filterKnowledgePointNode"
              @check="handleKnowledgePointCheck"
            >
              <template #default="{ data }">
                <div class="knowledge-node">
                  <span>{{ data.name }}</span>
                  <span class="knowledge-node-path">{{ data.path }}</span>
                </div>
              </template>
            </el-tree>
          </div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/course'
import { userApi } from '@/api/user'
import type { Chapter, Course, KnowledgePointNode, Resource, ResourceTagSuggestion, User } from '@/types'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const chapterFormRef = ref<FormInstance>()
const resourceFormRef = ref<FormInstance>()
const knowledgePointTreeRef = ref<any>()

const pageLoading = ref(false)
const savingCourse = ref(false)
const chapterSubmitting = ref(false)
const resourceSubmitting = ref(false)
const studentLoading = ref(false)
const suggestionLoading = ref(false)
const selectedResourceFile = ref<File | null>(null)

const chapters = ref<Chapter[]>([])
const resourceMap = ref<Record<number, Resource[]>>({})
const activeChapterKeys = ref<string[]>([])
const courseStatus = ref<Course['status'] | ''>('')
const coverPreviewUrl = ref('')
const selectedCoverFile = ref<File | null>(null)
const studentOptions = ref<User[]>([])
const knowledgePointTree = ref<KnowledgePointNode[]>([])
const knowledgePointMap = ref<Record<number, KnowledgePointNode>>({})
const leafKnowledgePointCount = ref(0)
const suggestions = ref<ResourceTagSuggestion[]>([])
const knowledgePointKeyword = ref('')
let temporaryCoverUrl = ''

const isEdit = computed(() => Boolean(route.params.id))
const courseId = computed(() => String(route.params.id ?? ''))
const hasLeafKnowledgePoints = computed(() => leafKnowledgePointCount.value > 0)

const form = reactive({ title: '', description: '', coverImage: '', visibilityType: 'PUBLIC' as Course['visibilityType'], visibleStudentIds: [] as number[] })
const chapterDialog = reactive({ visible: false, isEdit: false, chapterId: '', form: { title: '', description: '', orderIndex: 1 } })
const resourceDialog = reactive({
  visible: false,
  isEdit: false,
  chapterId: '',
  resourceId: '',
  form: { title: '', type: 'DOCUMENT' as Resource['type'], url: '', description: '', managedFile: false, knowledgePointIds: [] as number[], duration: 0, size: 0, orderIndex: 1 },
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

function flattenKnowledgePoints(nodes: KnowledgePointNode[]) {
  const nextMap: Record<number, KnowledgePointNode> = {}
  let leafCount = 0
  const walk = (items: KnowledgePointNode[]) => {
    items.forEach((item) => {
      nextMap[item.id] = item
      if (item.nodeType === 'POINT' && item.active) leafCount += 1
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  knowledgePointMap.value = nextMap
  leafKnowledgePointCount.value = leafCount
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

function handleResourceFileChange(file: UploadFile) {
  if (!file.raw) return
  selectedResourceFile.value = file.raw
  resourceDialog.form.size = file.raw.size
}

function courseStatusTag(status: Course['status']) { return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info' }
function courseStatusLabel(status: Course['status']) { return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status }
function resourceTypeLabel(type: Resource['type']) { return { VIDEO: '视频', DOCUMENT: '文档', SLIDE: '课件' }[type] ?? type }
function resourceTaggingLabel(status?: Resource['taggingStatus']) { return { CONFIRMED: '已标注', UNTAGGED: '待标注' }[status ?? 'UNTAGGED'] ?? '待标注' }
function resourceTaggingType(status?: Resource['taggingStatus']) { return { CONFIRMED: 'success', UNTAGGED: 'warning' }[status ?? 'UNTAGGED'] ?? 'warning' }
function formatDuration(seconds: number) { const minutes = Math.floor(seconds / 60); const remainingSeconds = seconds % 60; return `${minutes}:${String(remainingSeconds).padStart(2, '0')}` }
function formatFileSize(size: number) { if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`; if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`; return `${size} B` }
function knowledgePointLabel(id: number) { return knowledgePointMap.value[id]?.path ?? `#${id}` }

function applyKnowledgePointSelection(ids: number[]) {
  const nextIds = Array.from(new Set(ids.filter((id) => knowledgePointMap.value[id]?.nodeType === 'POINT')))
  resourceDialog.form.knowledgePointIds = nextIds
  nextTick(() => knowledgePointTreeRef.value?.setCheckedKeys(nextIds))
}

function removeSelectedKnowledgePoint(id: number) { applyKnowledgePointSelection(resourceDialog.form.knowledgePointIds.filter((item) => item !== id)) }
function applySuggestedKnowledgePoints(ids: number[]) { applyKnowledgePointSelection([...resourceDialog.form.knowledgePointIds, ...ids]) }
function clearKnowledgePointSelection() { applyKnowledgePointSelection([]) }
function filterKnowledgePointNode(keyword: string, data: KnowledgePointNode) { if (!keyword) return true; const normalized = keyword.trim().toLowerCase(); return data.name.toLowerCase().includes(normalized) || data.path.toLowerCase().includes(normalized) }
function handleKnowledgePointCheck(data: KnowledgePointNode, checkedInfo: { checkedKeys: Array<string | number> }) {
  const checkedIds = checkedInfo.checkedKeys.map((item) => Number(item))
  if (data.nodeType !== 'POINT' && checkedIds.includes(data.id)) {
    knowledgePointTreeRef.value?.setChecked(data.id, false, false)
    ElMessage.warning('只能选择叶子知识点')
  }
  applyKnowledgePointSelection(checkedIds)
}

function mergeStudentOptions(students: User[]) {
  const merged = new Map<number, User>()
  for (const student of [...studentOptions.value, ...students]) merged.set(student.id, student)
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

async function loadKnowledgePointTree() {
  const tree = await courseApi.listKnowledgePointTree({ activeOnly: true })
  knowledgePointTree.value = tree
  flattenKnowledgePoints(tree)
}

async function loadTagSuggestions() {
  suggestionLoading.value = true
  try {
    suggestions.value = await courseApi.previewResourceTagSuggestions({ title: resourceDialog.form.title, description: resourceDialog.form.description, type: resourceDialog.form.type })
    if (!suggestions.value.length) ElMessage.info('没有生成建议标签，请手动选择知识点')
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
  suggestions.value = []
  knowledgePointKeyword.value = ''
  resourceDialog.form.title = ''
  resourceDialog.form.type = 'DOCUMENT'
  resourceDialog.form.url = ''
  resourceDialog.form.description = ''
  resourceDialog.form.managedFile = false
  resourceDialog.form.knowledgePointIds = []
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
  if (form.visibleStudentIds.length) await ensureSelectedStudentsLoaded(form.visibleStudentIds)
}

async function loadCurriculum() {
  if (!isEdit.value) return
  const chapterList = await courseApi.listChapters(courseId.value)
  chapters.value = chapterList
  activeChapterKeys.value = chapterList.map((chapter) => String(chapter.id))
  const resourceEntries = await Promise.all(chapterList.map(async (chapter) => [chapter.id, await courseApi.listResources(String(chapter.id))] as const))
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
  nextTick(() => knowledgePointTreeRef.value?.setCheckedKeys([]))
}

function openEditResourceDialog(chapter: Chapter, resource: Resource) {
  resourceDialog.visible = true
  resourceDialog.isEdit = true
  resourceDialog.chapterId = String(chapter.id)
  resourceDialog.resourceId = String(resource.id)
  selectedResourceFile.value = null
  suggestions.value = []
  knowledgePointKeyword.value = ''
  resourceDialog.form.title = resource.title
  resourceDialog.form.type = resource.type
  resourceDialog.form.url = resource.managedFile ? '' : (resource.sourceUrl ?? resource.url)
  resourceDialog.form.description = resource.description ?? ''
  resourceDialog.form.managedFile = Boolean(resource.managedFile)
  resourceDialog.form.knowledgePointIds = [...(resource.knowledgePoints?.map((item) => item.id) ?? [])]
  resourceDialog.form.duration = resource.duration ?? 0
  resourceDialog.form.size = resource.size ?? 0
  resourceDialog.form.orderIndex = resource.orderIndex
  nextTick(() => applyKnowledgePointSelection(resourceDialog.form.knowledgePointIds))
}

async function handleSubmitResource() {
  await resourceFormRef.value?.validate()
  resourceSubmitting.value = true
  try {
    let resourceUrl = resourceDialog.form.url?.trim() ?? ''
    let resourceSize = resourceDialog.form.size || undefined
    if (selectedResourceFile.value) {
      const uploaded = await courseApi.uploadResourceFile(selectedResourceFile.value, resourceDialog.form.type)
      resourceUrl = uploaded.storageKey
      resourceSize = uploaded.size || resourceSize
    }
    const payload = {
      title: resourceDialog.form.title,
      type: resourceDialog.form.type,
      url: resourceUrl || undefined,
      description: resourceDialog.form.description || undefined,
      knowledgePointIds: resourceDialog.form.knowledgePointIds,
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
    await loadCurriculum()
  } finally {
    resourceSubmitting.value = false
  }
}

async function handleDeleteResource(resource: Resource) {
  await ElMessageBox.confirm(`确定删除资源“${resource.title}”吗？`, '删除资源', { type: 'warning' })
  await courseApi.deleteResource(String(resource.id))
  ElMessage.success('资源已删除')
  await loadCurriculum()
}

watch(knowledgePointKeyword, (keyword) => { knowledgePointTreeRef.value?.filter(keyword) })

onMounted(async () => {
  pageLoading.value = true
  try {
    await Promise.all([loadStudentOptions(), loadKnowledgePointTree()])
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
.page-subtitle,.card-subtitle,.field-tip,.knowledge-node-path,.suggestion-path { margin-top: 8px; color: #909399; font-size: 12px; }
.card-header,.chapter-header,.tree-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
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
.knowledge-tree { max-height: 260px; overflow: auto; border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 12px; }
.knowledge-node { display: flex; flex-direction: column; gap: 4px; padding: 2px 0; }
</style>

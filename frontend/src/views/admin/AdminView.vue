<template>
  <div class="page-container">
    <div class="page-header"><span class="page-title">系统管理</span></div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users" />
      <el-tab-pane label="教师申请" name="teacherApplications" />
      <el-tab-pane label="知识点体系" name="knowledgePoints" />
      <el-tab-pane label="课程管理" name="courses" />
      <el-tab-pane label="服务状态" name="serviceHealth" />
    </el-tabs>

    <el-card shadow="never" style="margin-top: 16px">
      <div v-if="activeTab === 'users'">
        <div class="toolbar">
          <el-form inline class="toolbar-form">
            <el-form-item label="关键词">
              <el-input
                v-model="userFilters.keyword"
                clearable
                placeholder="搜索用户名或邮箱"
                style="width: 220px"
                @keyup.enter="resetAndFetchUsers"
                @clear="resetAndFetchUsers"
              />
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="userFilters.role" clearable placeholder="全部角色" style="width: 140px" @change="resetAndFetchUsers">
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师" value="TEACHER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="userFilters.active" clearable placeholder="全部状态" style="width: 140px" @change="resetAndFetchUsers">
                <el-option label="启用" :value="true" />
                <el-option label="停用" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="resetAndFetchUsers">查询</el-button>
              <el-button :icon="Refresh" :loading="userLoading" @click="fetchUsers">刷新</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table :data="users" v-loading="userLoading">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }"><el-tag :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button :icon="View" link type="primary" @click="openUserDrawer(row)">查看</el-button>
              <el-button
                link
                :type="row.isActive ? 'warning' : 'success'"
                @click="toggleUserActive(row)"
              >
                {{ row.isActive ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!userLoading && users.length === 0" description="暂无用户" />
        <el-pagination
          v-if="userTotal > 0"
          v-model:current-page="userPage"
          v-model:page-size="userPageSize"
          :total="userTotal"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 20px; justify-content: flex-end"
          @change="fetchUsers"
        />
      </div>

      <div v-else-if="activeTab === 'teacherApplications'">
        <el-table :data="teacherApplications" v-loading="applicationLoading">
          <el-table-column prop="username" label="申请人" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }"><el-tag type="warning">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button type="success" link @click="approveApplication(row.id)">通过</el-button>
              <el-button type="danger" link @click="rejectApplication(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!applicationLoading && teacherApplications.length === 0" description="暂无待审批教师申请" />
      </div>

      <div v-else-if="activeTab === 'knowledgePoints'">
        <div class="toolbar">
          <el-input v-model="knowledgePointKeyword" clearable placeholder="搜索知识点" style="max-width: 320px" />
          <el-button type="primary" @click="openCreateRootDialog">新增学科</el-button>
        </div>
        <el-tree
          ref="knowledgeTreeRef"
          v-loading="knowledgePointLoading"
          class="knowledge-tree"
          node-key="id"
          default-expand-all
          :data="knowledgePointTree"
          :props="{ label: 'name', children: 'children' }"
          :filter-node-method="filterKnowledgePointNode"
        >
          <template #default="{ data }">
            <div class="knowledge-row">
              <div>
                <div class="knowledge-name">
                  <span>{{ data.name }}</span>
                  <el-tag size="small">{{ knowledgeTypeLabel(data.nodeType) }}</el-tag>
                  <el-tag size="small" :type="data.active ? 'success' : 'info'">{{ data.active ? '启用' : '停用' }}</el-tag>
                </div>
                <div class="knowledge-path">{{ data.path }}</div>
                <div v-if="data.keywords" class="knowledge-path">关键词：{{ data.keywords }}</div>
              </div>
              <div class="knowledge-actions">
                <el-button v-if="data.nodeType !== 'POINT'" link type="primary" @click.stop="openCreateChildDialog(data)">新增下级</el-button>
                <el-button link type="primary" @click.stop="openEditDialog(data)">编辑</el-button>
                <el-button link :type="data.active ? 'warning' : 'success'" @click.stop="toggleKnowledgePoint(data)">{{ data.active ? '停用' : '启用' }}</el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <div v-else-if="activeTab === 'serviceHealth'">
        <div class="toolbar">
          <div>
            <div class="health-summary">
              <el-tag :type="serviceHealthSummary.down === 0 ? 'success' : 'danger'">
                {{ serviceHealthSummary.down === 0 ? '全部正常' : `${serviceHealthSummary.down} 个异常` }}
              </el-tag>
              <span class="health-summary-text">
                已检查 {{ serviceHealthSummary.total }} 个服务
              </span>
            </div>
            <div class="knowledge-path">最后检查：{{ lastHealthCheckedAt ? formatDateTime(lastHealthCheckedAt) : '尚未检查' }}</div>
          </div>
          <el-button type="primary" :loading="serviceHealthLoading" @click="fetchServiceHealth">刷新状态</el-button>
        </div>
        <el-table :data="serviceHealth" v-loading="serviceHealthLoading">
          <el-table-column prop="name" label="服务" min-width="150" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'UP' ? 'success' : 'danger'">{{ row.status === 'UP' ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="响应耗时" width="120">
            <template #default="{ row }">{{ row.responseTimeMs ?? '-' }} ms</template>
          </el-table-column>
          <el-table-column label="HTTP" width="100">
            <template #default="{ row }">{{ row.httpStatus ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="endpoint" label="检查路径" min-width="180" />
          <el-table-column label="最近检查" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.checkedAt) }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="{ row }">{{ row.message ?? '健康检查通过' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="activeTab === 'courses'">
        <div class="toolbar">
          <el-form inline class="toolbar-form">
            <el-form-item label="关键词">
              <el-input
                v-model="courseFilters.keyword"
                clearable
                placeholder="搜索课程名称"
                style="width: 220px"
                @keyup.enter="resetAndFetchCourses"
                @clear="resetAndFetchCourses"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="courseFilters.status" clearable placeholder="全部状态" style="width: 140px" @change="resetAndFetchCourses">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已归档" value="ARCHIVED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="resetAndFetchCourses">查询</el-button>
              <el-button :icon="Refresh" :loading="courseLoading" @click="fetchCourses">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" :icon="Plus" @click="router.push('/courses/create')">新建课程</el-button>
        </div>

        <el-table :data="courses" v-loading="courseLoading">
          <el-table-column prop="title" label="课程名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="teacherName" label="授课教师" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="courseStatusTagType(row.status)">{{ courseStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可见范围" min-width="150">
            <template #default="{ row }">{{ courseVisibilityLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button :icon="View" link type="primary" @click="router.push(`/courses/${row.id}`)">查看</el-button>
              <el-button :icon="Edit" link type="primary" @click="router.push(`/courses/${row.id}/edit`)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handlePublishCourse(row)">发布</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="handleUnpublishCourse(row)">下架</el-button>
              <el-button v-if="row.status !== 'ARCHIVED'" link type="warning" @click="handleArchiveCourse(row)">归档</el-button>
              <el-button v-if="row.status === 'ARCHIVED'" link type="success" @click="handleRestoreCourse(row)">恢复</el-button>
              <el-button :icon="Delete" link type="danger" @click="handleDeleteCourse(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!courseLoading && courses.length === 0" description="暂无课程" />
        <el-pagination
          v-if="courseTotal > 0"
          v-model:current-page="coursePage"
          v-model:page-size="coursePageSize"
          :total="courseTotal"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 20px; justify-content: flex-end"
          @change="fetchCourses"
        />
      </div>
    </el-card>

    <el-dialog v-model="knowledgePointDialog.visible" :title="knowledgePointDialog.isEdit ? '编辑知识点' : '新增知识点'" width="560px">
      <el-form ref="knowledgePointFormRef" :model="knowledgePointDialog.form" :rules="knowledgePointRules" label-width="100px">
        <el-form-item label="节点名称" prop="name"><el-input v-model="knowledgePointDialog.form.name" placeholder="请输入节点名称" /></el-form-item>
        <el-form-item label="节点类型"><el-input :model-value="knowledgeTypeLabel(knowledgePointDialog.form.nodeType)" disabled /></el-form-item>
        <el-form-item label="节点描述"><el-input v-model="knowledgePointDialog.form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="knowledgePointDialog.form.keywords" placeholder="多个关键词用逗号分隔" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="knowledgePointDialog.form.orderIndex" :min="1" :max="999" /></el-form-item>
        <el-form-item label="是否启用"><el-switch v-model="knowledgePointDialog.form.active" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="knowledgePointDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="knowledgePointSubmitting" @click="handleSubmitKnowledgePoint">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="userDrawer.visible" title="用户详情" size="420px">
      <el-descriptions v-if="userDrawer.user" :column="1" border>
        <el-descriptions-item label="用户ID">{{ userDrawer.user.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ userDrawer.user.username }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ userDrawer.user.email }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="roleTagType(userDrawer.user.role)">{{ roleLabel(userDrawer.user.role) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="userDrawer.user.isActive ? 'success' : 'info'">{{ userDrawer.user.isActive ? '启用' : '停用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(userDrawer.user.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/course'
import { systemApi, type ServiceHealthResult } from '@/api/system'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { Course, KnowledgePointNode, TeacherRegistrationApplication, User } from '@/types'

const userStore = useUserStore()
const router = useRouter()
const activeTab = ref('users')
const users = ref<User[]>([])
const teacherApplications = ref<TeacherRegistrationApplication[]>([])
const courses = ref<Course[]>([])
const userLoading = ref(false)
const applicationLoading = ref(false)
const courseLoading = ref(false)
const knowledgePointLoading = ref(false)
const knowledgePointSubmitting = ref(false)
const serviceHealthLoading = ref(false)
const knowledgePointKeyword = ref('')
const knowledgePointTree = ref<KnowledgePointNode[]>([])
const serviceHealth = ref<ServiceHealthResult[]>([])
const lastHealthCheckedAt = ref('')
const coursePage = ref(1)
const coursePageSize = ref(10)
const courseTotal = ref(0)
const courseFilters = reactive({ keyword: '', status: '' })
const userPage = ref(1)
const userPageSize = ref(10)
const userTotal = ref(0)
const userFilters = reactive({
  keyword: '',
  role: '',
  active: undefined as boolean | undefined,
})
const knowledgeTreeRef = ref<any>()
const knowledgePointFormRef = ref<FormInstance>()
const userDrawer = ref({
  visible: false,
  user: null as User | null,
})

const knowledgePointDialog = ref({
  visible: false,
  isEdit: false,
  knowledgePointId: '',
  form: {
    parentId: null as number | null,
    name: '',
    description: '',
    keywords: '',
    nodeType: 'SUBJECT' as KnowledgePointNode['nodeType'],
    active: true,
    orderIndex: 1,
  },
})

const knowledgePointRules: FormRules = {
  name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
}

const serviceHealthSummary = computed(() => ({
  total: serviceHealth.value.length,
  down: serviceHealth.value.filter((item) => item.status !== 'UP').length,
}))

function roleTagType(role: string) { return { STUDENT: 'info', TEACHER: 'success', ADMIN: 'danger' }[role] ?? 'info' }
function roleLabel(role: string) { return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role] ?? role }
function statusLabel(status: string) { return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝' }[status] ?? status }
function courseStatusTagType(status: Course['status']) { return { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[status] ?? 'info' }
function courseStatusLabel(status: Course['status']) { return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] ?? status }
function courseVisibilityLabel(course: Course) {
  if (course.visibilityType === 'SELECTED_STUDENTS') {
    return `指定学生${course.visibleStudentCount ? ` · ${course.visibleStudentCount} 人` : ''}`
  }
  return '全体学生'
}
function knowledgeTypeLabel(type: KnowledgePointNode['nodeType']) { return { SUBJECT: '学科', DOMAIN: '知识领域', POINT: '知识点' }[type] ?? type }
function nextKnowledgePointType(type: KnowledgePointNode['nodeType']) { return { SUBJECT: 'DOMAIN', DOMAIN: 'POINT', POINT: 'POINT' }[type] as KnowledgePointNode['nodeType'] }
function filterKnowledgePointNode(keyword: string, data: KnowledgePointNode) { if (!keyword) return true; const normalized = keyword.trim().toLowerCase(); return data.name.toLowerCase().includes(normalized) || data.path.toLowerCase().includes(normalized) }
function formatDateTime(value: string) { return new Date(value).toLocaleString('zh-CN') }

async function fetchUsers() {
  userLoading.value = true
  try {
    const response = await userApi.listUsers({
      page: userPage.value,
      pageSize: userPageSize.value,
      keyword: userFilters.keyword || undefined,
      role: userFilters.role || undefined,
      active: userFilters.active,
    })
    users.value = response.items
    userTotal.value = response.total
  } finally {
    userLoading.value = false
  }
}

function resetAndFetchUsers() {
  userPage.value = 1
  fetchUsers()
}

function openUserDrawer(user: User) {
  userDrawer.value.user = user
  userDrawer.value.visible = true
}

async function toggleUserActive(user: User) {
  if (user.isActive) {
    await ElMessageBox.confirm(`确认将用户“${user.username}”标记为停用吗？`, '停用用户', { type: 'warning' })
  }
  const updatedUser = user.isActive
    ? await userApi.deactivateUser(user.id)
    : await userApi.activateUser(user.id)
  users.value = users.value.map((item) => item.id === updatedUser.id ? updatedUser : item)
  if (userDrawer.value.user?.id === updatedUser.id) {
    userDrawer.value.user = updatedUser
  }
  ElMessage.success(updatedUser.isActive ? '用户已启用' : '用户已停用')
}

async function fetchTeacherApplications() {
  applicationLoading.value = true
  try {
    teacherApplications.value = await userApi.listPendingTeacherRegistrationApplications()
  } finally {
    applicationLoading.value = false
  }
}

async function fetchKnowledgePoints() {
  knowledgePointLoading.value = true
  try {
    knowledgePointTree.value = await courseApi.listKnowledgePointTree({ activeOnly: false })
  } finally {
    knowledgePointLoading.value = false
  }
}

async function fetchCourses() {
  courseLoading.value = true
  try {
    const response = await courseApi.listCourses({
      page: coursePage.value,
      pageSize: coursePageSize.value,
      keyword: courseFilters.keyword || undefined,
      status: courseFilters.status || undefined,
    })
    courses.value = response.items
    courseTotal.value = response.total
  } finally {
    courseLoading.value = false
  }
}

function resetAndFetchCourses() {
  coursePage.value = 1
  fetchCourses()
}

async function fetchServiceHealth() {
  serviceHealthLoading.value = true
  try {
    serviceHealth.value = await systemApi.listServiceHealth()
    lastHealthCheckedAt.value = new Date().toISOString()
  } finally {
    serviceHealthLoading.value = false
  }
}

function resetKnowledgePointDialog() {
  knowledgePointDialog.value = {
    visible: false,
    isEdit: false,
    knowledgePointId: '',
    form: { parentId: null, name: '', description: '', keywords: '', nodeType: 'SUBJECT', active: true, orderIndex: 1 },
  }
}

function openCreateRootDialog() {
  resetKnowledgePointDialog()
  knowledgePointDialog.value.visible = true
}

function openCreateChildDialog(node: KnowledgePointNode) {
  resetKnowledgePointDialog()
  knowledgePointDialog.value.visible = true
  knowledgePointDialog.value.form.parentId = node.id
  knowledgePointDialog.value.form.nodeType = nextKnowledgePointType(node.nodeType)
}

function openEditDialog(node: KnowledgePointNode) {
  knowledgePointDialog.value.visible = true
  knowledgePointDialog.value.isEdit = true
  knowledgePointDialog.value.knowledgePointId = String(node.id)
  knowledgePointDialog.value.form.parentId = node.parentId ?? null
  knowledgePointDialog.value.form.name = node.name
  knowledgePointDialog.value.form.description = node.description ?? ''
  knowledgePointDialog.value.form.keywords = node.keywords ?? ''
  knowledgePointDialog.value.form.nodeType = node.nodeType
  knowledgePointDialog.value.form.active = node.active
  knowledgePointDialog.value.form.orderIndex = node.orderIndex
}

async function handleSubmitKnowledgePoint() {
  await knowledgePointFormRef.value?.validate()
  knowledgePointSubmitting.value = true
  try {
    if (knowledgePointDialog.value.isEdit) {
      await courseApi.updateKnowledgePoint(knowledgePointDialog.value.knowledgePointId, knowledgePointDialog.value.form)
      ElMessage.success('知识点已更新')
    } else {
      await courseApi.createKnowledgePoint(knowledgePointDialog.value.form)
      ElMessage.success('知识点已创建')
    }
    knowledgePointDialog.value.visible = false
    await fetchKnowledgePoints()
  } finally {
    knowledgePointSubmitting.value = false
  }
}

async function toggleKnowledgePoint(node: KnowledgePointNode) {
  await courseApi.updateKnowledgePoint(String(node.id), {
    parentId: node.parentId ?? null,
    name: node.name,
    description: node.description ?? undefined,
    keywords: node.keywords ?? undefined,
    nodeType: node.nodeType,
    active: !node.active,
    orderIndex: node.orderIndex,
  })
  ElMessage.success(node.active ? '知识点已停用' : '知识点已启用')
  await fetchKnowledgePoints()
}

async function handlePublishCourse(course: Course) {
  const updatedCourse = await courseApi.publishCourse(String(course.id))
  replaceCourse(updatedCourse)
  ElMessage.success('课程已发布')
}

async function handleUnpublishCourse(course: Course) {
  await ElMessageBox.confirm(`确认下架课程“${course.title}”吗？`, '下架课程', { type: 'warning' })
  const updatedCourse = await courseApi.unpublishCourse(String(course.id))
  replaceCourse(updatedCourse)
  ElMessage.success('课程已下架')
}

async function handleArchiveCourse(course: Course) {
  await ElMessageBox.confirm(`确认归档课程“${course.title}”吗？`, '归档课程', { type: 'warning' })
  const updatedCourse = await courseApi.archiveCourse(String(course.id))
  replaceCourse(updatedCourse)
  ElMessage.success('课程已归档')
}

async function handleRestoreCourse(course: Course) {
  const updatedCourse = await courseApi.restoreCourse(String(course.id))
  replaceCourse(updatedCourse)
  ElMessage.success('课程已恢复为草稿')
}

async function handleDeleteCourse(course: Course) {
  await ElMessageBox.confirm(`确认删除课程“${course.title}”吗？该操作会删除课程下的章节和资源。`, '删除课程', { type: 'warning' })
  await courseApi.deleteCourse(String(course.id))
  ElMessage.success('课程已删除')
  await fetchCourses()
}

function replaceCourse(updatedCourse: Course) {
  courses.value = courses.value.map((course) => course.id === updatedCourse.id ? updatedCourse : course)
}

async function approveApplication(id: number) {
  if (!userStore.user) return
  try {
    await ElMessageBox.confirm('确认通过这条教师申请吗？', '审批确认', { type: 'warning' })
    await userApi.approveTeacherRegistrationApplication(id, { reviewerId: userStore.user.id })
    ElMessage.success('教师申请已通过')
    await Promise.all([fetchTeacherApplications(), fetchUsers()])
  } catch (_error) {
    // ignore cancel
  }
}

async function rejectApplication(id: number) {
  if (!userStore.user) return
  try {
    await ElMessageBox.confirm('确认拒绝这条教师申请吗？', '审批确认', { type: 'warning' })
    await userApi.rejectTeacherRegistrationApplication(id, { reviewerId: userStore.user.id })
    ElMessage.success('教师申请已拒绝')
    await fetchTeacherApplications()
  } catch (_error) {
    // ignore cancel
  }
}

watch(activeTab, async (tab) => {
  if (tab === 'users') await fetchUsers()
  if (tab === 'teacherApplications') await fetchTeacherApplications()
  if (tab === 'courses') await fetchCourses()
  if (tab === 'knowledgePoints') await fetchKnowledgePoints()
  if (tab === 'serviceHealth') await fetchServiceHealth()
})

watch(knowledgePointKeyword, (keyword) => {
  knowledgeTreeRef.value?.filter(keyword)
})

onMounted(async () => {
  await fetchUsers()
})
</script>

<style scoped>
.toolbar,.knowledge-row,.knowledge-actions,.knowledge-name,.health-summary { display: flex; align-items: center; gap: 12px; }
.toolbar,.knowledge-row { justify-content: space-between; }
.toolbar-form { align-items: center; }
.toolbar-form :deep(.el-form-item) { margin-bottom: 0; }
.knowledge-tree { padding: 8px 0; }
.knowledge-row { width: 100%; padding: 8px 0; }
.knowledge-path,.placeholder-text { color: #909399; font-size: 12px; }
.health-summary-text { color: #606266; font-size: 13px; }
</style>

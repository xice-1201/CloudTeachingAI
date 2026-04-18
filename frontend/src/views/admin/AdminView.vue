<template>
  <div class="page-container">
    <div class="page-header"><span class="page-title">系统管理</span></div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users" />
      <el-tab-pane label="教师申请" name="teacherApplications" />
      <el-tab-pane label="知识点体系" name="knowledgePoints" />
      <el-tab-pane label="课程管理" name="courses" />
    </el-tabs>

    <el-card shadow="never" style="margin-top: 16px">
      <div v-if="activeTab === 'users'">
        <el-table :data="users" v-loading="userLoading">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }"><el-tag :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
        </el-table>
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

      <div v-else>
        <p class="placeholder-text">课程管理功能将在后续阶段继续补齐。</p>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/course'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { KnowledgePointNode, TeacherRegistrationApplication, User } from '@/types'

const userStore = useUserStore()
const activeTab = ref('users')
const users = ref<User[]>([])
const teacherApplications = ref<TeacherRegistrationApplication[]>([])
const userLoading = ref(false)
const applicationLoading = ref(false)
const knowledgePointLoading = ref(false)
const knowledgePointSubmitting = ref(false)
const knowledgePointKeyword = ref('')
const knowledgePointTree = ref<KnowledgePointNode[]>([])
const knowledgeTreeRef = ref<any>()
const knowledgePointFormRef = ref<FormInstance>()

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

function roleTagType(role: string) { return { STUDENT: 'info', TEACHER: 'success', ADMIN: 'danger' }[role] ?? 'info' }
function roleLabel(role: string) { return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role] ?? role }
function statusLabel(status: string) { return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝' }[status] ?? status }
function knowledgeTypeLabel(type: KnowledgePointNode['nodeType']) { return { SUBJECT: '学科', DOMAIN: '知识领域', POINT: '知识点' }[type] ?? type }
function nextKnowledgePointType(type: KnowledgePointNode['nodeType']) { return { SUBJECT: 'DOMAIN', DOMAIN: 'POINT', POINT: 'POINT' }[type] as KnowledgePointNode['nodeType'] }
function filterKnowledgePointNode(keyword: string, data: KnowledgePointNode) { if (!keyword) return true; const normalized = keyword.trim().toLowerCase(); return data.name.toLowerCase().includes(normalized) || data.path.toLowerCase().includes(normalized) }

async function fetchUsers() {
  userLoading.value = true
  try {
    const [students, teachers] = await Promise.all([userApi.listStudents({ page: 1, pageSize: 100 }), userApi.listTeachers({ page: 1, pageSize: 100 })])
    users.value = [...teachers.items, ...students.items]
  } finally {
    userLoading.value = false
  }
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
  await courseApi.updateKnowledgePoint(String(node.id), { ...node, active: !node.active })
  ElMessage.success(node.active ? '知识点已停用' : '知识点已启用')
  await fetchKnowledgePoints()
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
  if (tab === 'knowledgePoints') await fetchKnowledgePoints()
})

watch(knowledgePointKeyword, (keyword) => {
  knowledgeTreeRef.value?.filter(keyword)
})

onMounted(async () => {
  await fetchUsers()
})
</script>

<style scoped>
.toolbar,.knowledge-row,.knowledge-actions,.knowledge-name { display: flex; align-items: center; gap: 12px; }
.toolbar,.knowledge-row { justify-content: space-between; }
.knowledge-tree { padding: 8px 0; }
.knowledge-row { width: 100%; padding: 8px 0; }
.knowledge-path,.placeholder-text { color: #909399; font-size: 12px; }
</style>

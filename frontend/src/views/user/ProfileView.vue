<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">个人资料</span>
    </div>

    <el-card shadow="never" style="max-width: 600px" v-loading="loading">
      <div class="avatar-section">
        <el-avatar :size="80" :src="form.avatar">
          {{ (form.username || userStore.user?.username)?.[0]?.toUpperCase() }}
        </el-avatar>
        <div class="avatar-actions">
          <el-upload action="#" :auto-upload="false" :show-file-list="false" accept="image/*" @change="handleAvatarChange">
            <el-button size="small">更换头像</el-button>
          </el-upload>
          <el-button v-if="form.avatar" size="small" type="danger" plain @click="handleAvatarRemove">删除头像</el-button>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" style="margin-top: 24px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input :value="userStore.user?.email" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-tag>{{ roleLabel(userStore.user?.role) }}</el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  avatar: '',
})
const rules: FormRules = {
  username: [
    {
      validator: (_rule, value: string, callback) => {
        const username = value?.trim() ?? ''
        if (!username) {
          callback(new Error('请输入用户名'))
          return
        }
        if (username.length < 2 || username.length > 100) {
          callback(new Error('用户名长度应为 2 到 100 个字符'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function roleLabel(role?: string) {
  return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role ?? ''] ?? role
}

async function handleAvatarChange(file: UploadFile) {
  if (!file.raw) return
  const res = await userApi.uploadAvatar(file.raw)
  form.avatar = res.url
  await userStore.fetchProfile()
  form.avatar = userStore.user?.avatar ?? res.url
  ElMessage.success('头像上传成功')
}

function handleAvatarRemove() {
  form.avatar = ''
  ElMessage.info('头像已恢复默认，保存修改后将删除头像文件')
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await userApi.updateProfile({
      username: form.username.trim(),
      avatar: form.avatar,
    })
    await userStore.fetchProfile()
    form.username = userStore.user?.username ?? form.username.trim()
    form.avatar = userStore.user?.avatar ?? form.avatar
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await userStore.fetchProfile()
    form.username = userStore.user?.username ?? ''
    form.avatar = userStore.user?.avatar ?? ''
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.avatar-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>

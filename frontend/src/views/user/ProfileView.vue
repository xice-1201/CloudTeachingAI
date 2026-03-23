<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">个人资料</span>
    </div>

    <el-card shadow="never" style="max-width: 600px" v-loading="loading">
      <div class="avatar-section">
        <el-avatar :size="80" :src="form.avatar">
          {{ userStore.user?.username?.[0]?.toUpperCase() }}
        </el-avatar>
        <el-upload action="#" :auto-upload="false" :show-file-list="false" accept="image/*" @change="handleAvatarChange">
          <el-button size="small" style="margin-top: 8px">更换头像</el-button>
        </el-upload>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" style="margin-top: 24px">
        <el-form-item label="用户名">
          <el-input :value="userStore.user?.username" disabled />
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
import type { FormRules, UploadFile } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const form = reactive({ avatar: '' })
const rules: FormRules = {}

function roleLabel(role?: string) {
  return { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }[role ?? ''] ?? role
}

async function handleAvatarChange(file: UploadFile) {
  if (!file.raw) return
  const res = await userApi.uploadAvatar(file.raw)
  form.avatar = res.url
  if (userStore.user) userStore.user.avatar = res.url
}

async function handleSave() {
  saving.value = true
  try {
    await userApi.updateProfile({ avatar: form.avatar })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await userStore.fetchProfile()
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
</style>

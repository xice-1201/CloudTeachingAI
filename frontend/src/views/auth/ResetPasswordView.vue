<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>重置密码</h1>
        <p>输入邮箱接收重置链接</p>
      </div>
      <el-form v-if="!sent" ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleSend">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="注册邮箱" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
            发送重置邮件
          </el-button>
        </el-form-item>
        <div class="login-footer">
          <el-link type="primary" @click="$router.push('/login')">返回登录</el-link>
        </div>
      </el-form>
      <el-result v-else icon="success" title="邮件已发送" sub-title="请检查您的邮箱并点击重置链接">
        <template #extra>
          <el-button type="primary" @click="$router.push('/login')">返回登录</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'

const formRef = ref<FormInstance>()
const loading = ref(false)
const sent = ref(false)
const form = reactive({ email: '' })

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
}

async function handleSend() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await authApi.sendResetEmail(form.email)
    sent.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a73e8 0%, #0d47a1 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.login-header {
  text-align: center;
  margin-bottom: 36px;
}
.login-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: #1a73e8;
  margin-bottom: 8px;
}
.login-header p {
  color: #909399;
  font-size: 14px;
}
.login-footer {
  text-align: right;
}
</style>

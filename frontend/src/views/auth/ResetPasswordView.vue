<template>
  <div class="reset-password-page">
    <div class="reset-card">
      <div class="reset-header">
        <h1>重置密码</h1>
        <p>{{ step === 1 ? '输入您的邮箱地址，我们将发送重置链接' : '设置新密码' }}</p>
      </div>

      <!-- 步骤 1: 发送重置邮件 -->
      <el-form
        v-if="step === 1 && !sent"
        ref="emailFormRef"
        :model="emailForm"
        :rules="emailRules"
        size="large"
        @submit.prevent="handleSendEmail"
      >
        <el-form-item prop="email">
          <el-input v-model="emailForm.email" placeholder="邮箱地址" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
            发送重置链接
          </el-button>
        </el-form-item>
        <div class="reset-footer">
          <el-link type="primary" @click="$router.push('/login')">返回登录</el-link>
        </div>
      </el-form>

      <!-- 邮件发送成功提示 -->
      <el-result v-if="sent" icon="success" title="邮件已发送" sub-title="请检查您的邮箱并点击重置链接">
        <template #extra>
          <el-button type="primary" @click="$router.push('/login')">返回登录</el-button>
        </template>
      </el-result>

      <!-- 步骤 2: 设置新密码 -->
      <el-form
        v-if="step === 2 && !success"
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        size="large"
        @submit.prevent="handleResetPassword"
      >
        <el-form-item prop="password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="新密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="确认新密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleResetPassword"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
            重置密码
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 成功提示 -->
      <el-result v-if="success" icon="success" title="密码重置成功" sub-title="您现在可以使用新密码登录">
        <template #extra>
          <el-button type="primary" @click="$router.push('/login')">前往登录</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Message, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { rules } from '@/utils/validate'

const route = useRoute()

const emailFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const loading = ref(false)
const step = ref(1)
const sent = ref(false)
const success = ref(false)

const emailForm = reactive({
  email: '',
})

const passwordForm = reactive({
  password: '',
  confirmPassword: '',
})

const emailRules: FormRules = {
  email: [rules.required('请输入邮箱'), rules.email()],
}

const passwordRules: FormRules = {
  password: [rules.required('请输入新密码'), rules.password()],
  confirmPassword: [
    rules.required('请确认新密码'),
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

onMounted(() => {
  // 如果 URL 中有 token 参数，直接进入步骤 2
  const token = route.query.token as string
  if (token) {
    step.value = 2
  }
})

async function handleSendEmail() {
  if (!emailFormRef.value) return

  try {
    await emailFormRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await authApi.sendResetEmail(emailForm.email)
    sent.value = true
    ElMessage.success('重置链接已发送到您的邮箱，请查收')
  } catch (error) {
    console.error('Send reset email failed:', error)
  } finally {
    loading.value = false
  }
}

async function handleResetPassword() {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  const token = route.query.token as string
  if (!token) {
    ElMessage.error('重置链接无效')
    return
  }

  loading.value = true
  try {
    await authApi.resetPassword(token, passwordForm.password)
    success.value = true
    ElMessage.success('密码重置成功')
  } catch (error) {
    console.error('Reset password failed:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reset-password-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.reset-password-page::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: moveBackground 20s linear infinite;
}

@keyframes moveBackground {
  0% {
    transform: translate(0, 0);
  }
  100% {
    transform: translate(50px, 50px);
  }
}

.reset-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
}

.reset-header {
  text-align: center;
  margin-bottom: 36px;
}

.reset-header h1 {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.reset-header p {
  color: #909399;
  font-size: 14px;
  line-height: 1.6;
}

.reset-footer {
  text-align: center;
  margin-top: -8px;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
</style>

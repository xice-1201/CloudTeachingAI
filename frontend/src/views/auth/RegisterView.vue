<template>
  <div class="login-page">
    <div class="login-left">
      <div class="decor-content">
        <div class="decor-icon">
          <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
            <circle cx="100" cy="100" r="80" fill="rgba(255,255,255,0.1)" />
            <circle cx="60" cy="70" r="30" fill="rgba(255,255,255,0.15)" />
            <circle cx="140" cy="130" r="25" fill="rgba(255,255,255,0.12)" />
            <path d="M50 150 Q100 100 150 150" stroke="rgba(255,255,255,0.2)" stroke-width="3" fill="none" />
            <path d="M70 80 L130 80 L130 120 L70 120 Z" fill="rgba(255,255,255,0.08)" />
            <circle cx="85" cy="100" r="8" fill="rgba(255,255,255,0.2)" />
            <circle cx="115" cy="100" r="8" fill="rgba(255,255,255,0.2)" />
          </svg>
        </div>
        <h2 class="decor-title">CloudTeachingAI</h2>
        <p class="decor-subtitle">智能云端教学平台</p>
        <div class="decor-features">
          <div class="feature-item">
            <span class="feature-icon">📚</span>
            <span>课程资源管理</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🧭</span>
            <span>能力图谱测试</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🤖</span>
            <span>AI 智能助手</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">📊</span>
            <span>数据分析洞察</span>
          </div>
        </div>
      </div>
      <div class="decor-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
      </div>
    </div>

    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <h1>创建账户</h1>
          <p>加入智能教学平台</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="formRules" size="large" @submit.prevent="handleRegister">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="role">
            <el-radio-group v-model="form.role" class="role-group">
              <el-radio-button label="STUDENT">注册为学生</el-radio-button>
              <el-radio-button label="TEACHER">注册为教师</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="邮箱" :prefix-icon="Message" />
          </el-form-item>
          <el-form-item prop="code">
            <div class="code-input">
              <el-input v-model="form.code" placeholder="验证码" :prefix-icon="Key" maxlength="6" />
              <el-button
                native-type="button"
                :disabled="countdown > 0 || sendingCode"
                :loading="sendingCode"
                @click.prevent="handleSendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
              注册
            </el-button>
          </el-form-item>
          <div class="login-footer">
            <span class="footer-text">已有账户？</span>
            <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Message, Lock, Key } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { rules } from '@/utils/validate'
import { authApi } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  username: '',
  role: 'STUDENT',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const formRules: FormRules = {
  username: [rules.required('请输入用户名'), rules.minLength(2, '用户名至少 2 个字符')],
  role: [rules.required('请选择注册身份')],
  email: [rules.required('请输入邮箱'), rules.email()],
  code: [rules.required('请输入验证码'), rules.minLength(6, '验证码为 6 位数字')],
  password: [rules.required('请输入密码'), rules.minLength(6, '密码至少 6 位')],
  confirmPassword: [
    rules.required('请确认密码'),
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleSendCode() {
  if (sendingCode.value) return

  sendingCode.value = true
  try {
    await formRef.value?.validateField('email')
  } catch {
    sendingCode.value = false
    return
  }

  try {
    await authApi.sendVerificationCode({ email: form.email })
    ElMessage.success('验证码已发送，请查收邮件')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (error) {
    console.error('Send code failed:', error)
  } finally {
    sendingCode.value = false
  }
}

async function handleRegister() {
  if (!formRef.value || loading.value) return

  loading.value = true
  try {
    await formRef.value.validate()
  } catch {
    loading.value = false
    return
  }

  try {
    await authApi.register({
      username: form.username,
      email: form.email,
      code: form.code,
      password: form.password,
      role: form.role,
    })

    if (form.role === 'TEACHER') {
      ElMessage.success('教师注册申请已提交，请等待管理员审核通过后再登录')
      form.username = ''
      form.email = ''
      form.code = ''
      form.password = ''
      form.confirmPassword = ''
      return
    }

    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    console.error('Register failed:', error)
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.decor-content {
  text-align: center;
  color: #fff;
  z-index: 2;
  padding: 40px;
}

.decor-icon {
  width: 120px;
  height: 120px;
  margin: 0 auto 24px;
}

.decor-icon svg {
  width: 100%;
  height: 100%;
}

.decor-title {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 8px;
  letter-spacing: 1px;
}

.decor-subtitle {
  font-size: 18px;
  opacity: 0.9;
  margin-bottom: 48px;
}

.decor-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-width: 400px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.1);
  padding: 12px 16px;
  border-radius: 8px;
  backdrop-filter: blur(10px);
  font-size: 14px;
}

.feature-icon {
  font-size: 20px;
}

.decor-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}

.shape-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  left: -100px;
}

.shape-2 {
  width: 200px;
  height: 200px;
  bottom: 10%;
  right: 5%;
}

.shape-3 {
  width: 150px;
  height: 150px;
  top: 20%;
  right: 10%;
  background: rgba(255, 255, 255, 0.03);
}

.login-right {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 40px;
}

.login-card {
  width: 100%;
  max-width: 380px;
}

.login-header {
  margin-bottom: 36px;
}

.login-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.login-header p {
  color: #909399;
  font-size: 14px;
}

.role-group {
  width: 100%;
  display: flex;
}

.role-group :deep(.el-radio-button) {
  flex: 1;
}

.role-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.code-input {
  display: flex;
  gap: 12px;
  width: 100%;
}

.code-input .el-input {
  flex: 1;
}

.code-input .el-button {
  width: 120px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

.footer-text {
  color: #909399;
  margin-right: 8px;
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

@media (max-width: 900px) {
  .login-left {
    display: none;
  }

  .login-right {
    width: 100%;
  }
}
</style>

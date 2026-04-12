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
            <span class="feature-icon">课程</span>
            <span>课程资源管理</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">能力</span>
            <span>能力图谱测试</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">AI</span>
            <span>AI 智能助手</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">分析</span>
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
          <h1>欢迎回来</h1>
          <p>请登录您的账户</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="formRules" size="large" @submit.prevent="handleLogin">
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="邮箱" :prefix-icon="Message" />
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
          <el-form-item>
            <div class="form-row">
              <el-checkbox v-model="form.remember">记住账户</el-checkbox>
              <el-link type="primary" @click="$router.push('/reset-password')">忘记密码？</el-link>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
              登录
            </el-button>
          </el-form-item>
          <div class="login-footer">
            <span class="footer-text">还没有账户？</span>
            <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { rules } from '@/utils/validate'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  email: '',
  password: '',
  remember: false,
})

const formRules: FormRules = {
  email: [rules.required('请输入邮箱'), rules.email()],
  password: [rules.required('请输入密码'), rules.minLength(6, '密码至少 6 位')],
}

async function handleLogin() {
  if (!formRef.value || loading.value) return

  loading.value = true

  try {
    await formRef.value.validate()
    await userStore.login(form.email, form.password)
    ElMessage.success('登录成功')
    const defaultRedirect = userStore.isAdmin ? '/admin' : '/dashboard'
    const redirect = (route.query.redirect as string) || defaultRedirect
    router.push(redirect)
  } catch (error) {
    if (error) {
      console.error('Login failed:', error)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (route.query.expired === '1') {
    ElMessage.warning('身份验证已过期，请重新登录')
    const nextQuery = { ...route.query }
    delete nextQuery.expired
    router.replace({ name: 'Login', query: nextQuery })
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
  min-width: 36px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
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

.form-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
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
<template>
  <el-result v-if="error" icon="error" :title="errorTitle" :sub-title="errorMessage">
    <template #extra>
      <el-button type="primary" @click="handleRetry">重试</el-button>
      <el-button @click="handleBack">返回</el-button>
    </template>
  </el-result>
  <slot v-else />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

interface Props {
  error?: Error | null
  errorTitle?: string
  errorMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  error: null,
  errorTitle: '加载失败',
  errorMessage: '抱歉，页面加载失败，请稍后重试',
})

const emit = defineEmits<{
  retry: []
}>()

const router = useRouter()

function handleRetry() {
  emit('retry')
}

function handleBack() {
  router.back()
}
</script>

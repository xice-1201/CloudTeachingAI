<template>
  <div class="file-upload">
    <el-upload
      ref="uploadRef"
      :action="uploadUrl"
      :headers="headers"
      :data="extraData"
      :multiple="multiple"
      :limit="limit"
      :accept="accept"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-exceed="handleExceed"
      :on-progress="handleProgress"
      :file-list="fileList"
      :auto-upload="autoUpload"
      :show-file-list="showFileList"
      :list-type="listType"
    >
      <slot>
        <el-button type="primary">
          <el-icon class="el-icon--left"><Upload /></el-icon>
          {{ buttonText }}
        </el-button>
      </slot>
      <template #tip>
        <div v-if="tip" class="el-upload__tip">{{ tip }}</div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadInstance, UploadProps, UploadUserFile } from 'element-plus'
import { formatFileSize } from '@/utils/format'

interface Props {
  modelValue?: UploadUserFile[]
  uploadUrl?: string
  accept?: string
  multiple?: boolean
  limit?: number
  maxSize?: number // MB
  autoUpload?: boolean
  showFileList?: boolean
  listType?: 'text' | 'picture' | 'picture-card'
  buttonText?: string
  tip?: string
  extraData?: Record<string, any>
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  uploadUrl: '/api/v1/uploads',
  accept: '*',
  multiple: false,
  limit: 1,
  maxSize: 100, // 默认 100MB
  autoUpload: true,
  showFileList: true,
  listType: 'text',
  buttonText: '选择文件',
})

const emit = defineEmits<{
  'update:modelValue': [files: UploadUserFile[]]
  success: [response: any, file: UploadUserFile]
  error: [error: Error, file: UploadUserFile]
  progress: [percent: number, file: UploadUserFile]
}>()

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>(props.modelValue)

const headers = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  // 文件大小检查
  const maxSizeBytes = props.maxSize * 1024 * 1024
  if (file.size > maxSizeBytes) {
    ElMessage.error(`文件大小不能超过 ${formatFileSize(maxSizeBytes)}`)
    return false
  }

  // 文件类型检查
  if (props.accept !== '*') {
    const acceptTypes = props.accept.split(',').map((t) => t.trim())
    const fileType = file.type
    const fileName = file.name
    const fileExt = fileName.substring(fileName.lastIndexOf('.')).toLowerCase()

    const isAccepted = acceptTypes.some((type) => {
      if (type.startsWith('.')) {
        return fileExt === type.toLowerCase()
      }
      return fileType.match(new RegExp(type.replace('*', '.*')))
    })

    if (!isAccepted) {
      ElMessage.error(`只支持上传 ${props.accept} 格式的文件`)
      return false
    }
  }

  return true
}

const handleSuccess: UploadProps['onSuccess'] = (response, file) => {
  emit('success', response, file)
  emit('update:modelValue', fileList.value)
}

const handleError: UploadProps['onError'] = (error, file) => {
  ElMessage.error('文件上传失败')
  emit('error', error as Error, file)
}

const handleExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning(`最多只能上传 ${props.limit} 个文件`)
}

const handleProgress: UploadProps['onProgress'] = (event, file) => {
  emit('progress', event.percent || 0, file)
}

// 暴露方法
defineExpose({
  submit: () => uploadRef.value?.submit(),
  clearFiles: () => uploadRef.value?.clearFiles(),
})
</script>

<style scoped>
.file-upload {
  width: 100%;
}
</style>

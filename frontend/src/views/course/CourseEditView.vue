<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ isEdit ? '编辑课程' : '创建课程' }}</span>
    </div>

    <el-card shadow="never" style="max-width: 800px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" size="large">
        <el-form-item label="课程名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="课程描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入课程描述" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            @change="handleCoverChange"
          >
            <div class="cover-uploader">
              <img v-if="form.coverImage" :src="form.coverImage" class="cover-preview" />
              <div v-else class="cover-placeholder">
                <el-icon :size="32" color="#c0c4cc"><Plus /></el-icon>
                <span>上传封面</span>
              </div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '创建课程' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { courseApi } from '@/api/course'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  title: '',
  description: '',
  coverImage: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入课程描述', trigger: 'blur' }],
}

function handleCoverChange(file: UploadFile) {
  if (file.raw) {
    form.coverImage = URL.createObjectURL(file.raw)
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    if (isEdit.value) {
      await courseApi.updateCourse(route.params.id as string, form)
      ElMessage.success('课程已更新')
    } else {
      const course = await courseApi.createCourse(form)
      ElMessage.success('课程已创建')
      router.push(`/courses/${course.id}`)
      return
    }
    router.back()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const course = await courseApi.getCourse(route.params.id as string)
    Object.assign(form, { title: course.title, description: course.description, coverImage: course.coverImage ?? '' })
  }
})
</script>

<style scoped>
.cover-uploader {
  width: 200px;
  height: 120px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
}
.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
}
</style>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <span class="page-title">个性化学习路线</span>
      <el-button type="primary" :loading="generating" @click="handleGenerate">
        AI 重新生成
      </el-button>
    </div>

    <el-empty v-if="!loading && !path" description="暂无学习路线，点击「AI 重新生成」获取个性化推荐" />

    <div v-else-if="path" class="path-container">
      <div v-for="(item, index) in path.resources" :key="item.resourceId" class="path-step">
        <div class="step-index">{{ index + 1 }}</div>
        <div class="step-card" @click="$router.push(`/courses/${item.resourceId}/learn/${item.resourceId}`)">
          <div class="step-title">{{ item.resourceTitle }}</div>
          <div class="step-course">{{ item.courseTitle }}</div>
          <div class="step-reason">
            <el-icon color="#909399"><InfoFilled /></el-icon>
            {{ item.reason }}
          </div>
        </div>
        <div v-if="index < path.resources.length - 1" class="step-connector" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { learnApi } from '@/api/learn'
import type { LearningPath } from '@/types'

const loading = ref(false)
const generating = ref(false)
const path = ref<LearningPath | null>(null)

async function handleGenerate() {
  generating.value = true
  try {
    path.value = await learnApi.generateLearningPath()
    ElMessage.success('学习路线已更新')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    path.value = await learnApi.getLearningPath().catch(() => null)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.path-container { max-width: 700px; }
.path-step { display: flex; flex-direction: column; align-items: flex-start; position: relative; padding-left: 52px; }
.step-index {
  position: absolute;
  left: 0;
  top: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  z-index: 1;
}
.step-card {
  width: 100%;
  padding: 16px 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s;
  margin-bottom: 0;
}
.step-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.step-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.step-course { font-size: 13px; color: #909399; margin-bottom: 8px; }
.step-reason { display: flex; align-items: flex-start; gap: 6px; font-size: 13px; color: #606266; line-height: 1.5; }
.step-connector {
  width: 2px;
  height: 24px;
  background: #e4e7ed;
  margin-left: 17px;
  margin-top: 0;
  margin-bottom: 0;
}
</style>

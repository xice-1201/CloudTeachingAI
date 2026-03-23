<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">能力测试</span>
    </div>

    <!-- 未开始 -->
    <el-card v-if="!sessionId" shadow="never" style="max-width: 600px">
      <el-form label-width="100px">
        <el-form-item label="知识点">
          <el-select v-model="selectedKp" placeholder="选择要测试的知识点" style="width: 100%">
            <el-option v-for="kp in knowledgePoints" :key="kp.id" :label="kp.name" :value="kp.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedKp" :loading="loading" @click="startTest">
            开始测试
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 测试中 -->
    <el-card v-else-if="!completed" shadow="never" style="max-width: 700px">
      <div class="question-header">
        <span class="question-label">题目</span>
        <el-tag type="info">自适应测试</el-tag>
      </div>
      <div class="question-content">{{ currentQuestion?.content }}</div>
      <div class="options">
        <div
          v-for="opt in currentQuestion?.options"
          :key="opt.key"
          class="option-item"
          :class="{ selected: selectedAnswer === opt.key }"
          @click="selectedAnswer = opt.key"
        >
          <span class="option-key">{{ opt.key }}</span>
          <span>{{ opt.text }}</span>
        </div>
      </div>
      <el-button type="primary" :disabled="!selectedAnswer" :loading="loading" @click="submitAnswer" style="margin-top: 20px">
        提交答案
      </el-button>
    </el-card>

    <!-- 完成 -->
    <el-result v-else icon="success" title="测试完成" :sub-title="`本次测试已完成，能力图谱已更新`">
      <template #extra>
        <el-button type="primary" @click="$router.push('/learning')">查看能力图谱</el-button>
        <el-button @click="reset">再次测试</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { learnApi } from '@/api/learn'

const sessionId = ref('')
const selectedKp = ref('')
const selectedAnswer = ref('')
const currentQuestion = ref<any>(null)
const completed = ref(false)
const loading = ref(false)
// Placeholder — real data comes from course-service knowledge points API
const knowledgePoints = ref<{ id: string; name: string }[]>([])

async function startTest() {
  loading.value = true
  try {
    const res = await learnApi.startAbilityTest(selectedKp.value)
    sessionId.value = res.sessionId
    currentQuestion.value = res.question
  } finally {
    loading.value = false
  }
}

async function submitAnswer() {
  loading.value = true
  try {
    const res = await learnApi.submitAnswer(sessionId.value, currentQuestion.value.id, selectedAnswer.value)
    selectedAnswer.value = ''
    if (res.completed) {
      completed.value = true
    } else {
      currentQuestion.value = res.nextQuestion
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  sessionId.value = ''
  selectedKp.value = ''
  selectedAnswer.value = ''
  currentQuestion.value = null
  completed.value = false
}
</script>

<style scoped>
.question-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.question-label { font-size: 16px; font-weight: 600; color: #303133; }
.question-content {
  font-size: 15px;
  color: #303133;
  line-height: 1.7;
  margin-bottom: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
}
.options { display: flex; flex-direction: column; gap: 10px; }
.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.option-item:hover { border-color: #409eff; background: #ecf5ff; }
.option-item.selected { border-color: #409eff; background: #ecf5ff; color: #409eff; }
.option-key {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.option-item.selected .option-key { background: #409eff; color: #fff; }
</style>

<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">能力测试</span>
    </div>

    <el-card v-if="!sessionId" shadow="never" class="setup-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">开始一次知识点自评</div>
            <div class="card-subtitle">先选择一个知识点范围，系统会按该范围生成 1-6 道掌握度题目。</div>
          </div>
          <el-tag type="info">规则版诊断</el-tag>
        </div>
      </template>

      <el-form label-width="110px">
        <el-form-item label="测试范围">
          <el-select
            v-model="selectedKnowledgePointId"
            filterable
            placeholder="请选择知识点或知识领域"
            style="width: 100%"
          >
            <el-option
              v-for="item in knowledgePointOptions"
              :key="item.id"
              :label="item.path"
              :value="item.id"
            >
              <div class="option-content">
                <span>{{ item.path }}</span>
                <el-tag size="small" effect="plain">{{ knowledgeTypeLabel(item.nodeType) }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <div class="setup-tips">
            <p>适合在学习新课程前，或阶段性复盘时使用。</p>
            <p>测试结果会同步到学习中心，并立即刷新个性化学习路线。</p>
            <p>当前版本以自评题为主，后续可继续接入更细的 AI 诊断题。</p>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedKnowledgePointId" :loading="loading" @click="startTest">
            开始测试
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-else-if="currentQuestion && !completed" shadow="never" class="question-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">{{ currentQuestion.knowledgePointName }}</div>
            <div class="card-subtitle">
              第 {{ currentQuestion.orderIndex }} / {{ currentQuestion.totalQuestions }} 题
            </div>
          </div>
          <el-progress
            :percentage="Math.round((currentQuestion.orderIndex / currentQuestion.totalQuestions) * 100)"
            :stroke-width="8"
            class="header-progress"
          />
        </div>
      </template>

      <div class="question-content">{{ currentQuestion.content }}</div>
      <div class="options">
        <button
          v-for="option in currentQuestion.options"
          :key="option.key"
          type="button"
          class="option-item"
          :class="{ selected: selectedAnswer === option.key }"
          @click="selectedAnswer = option.key"
        >
          <span class="option-key">{{ option.key }}</span>
          <span class="option-text">{{ option.text }}</span>
        </button>
      </div>

      <div class="question-actions">
        <el-button @click="reset">重新选择范围</el-button>
        <el-button type="primary" :disabled="!selectedAnswer" :loading="loading" @click="submitAnswer">
          提交并继续
        </el-button>
      </div>
    </el-card>

    <el-result
      v-else
      icon="success"
      title="能力测试已完成"
      :sub-title="completedSummary"
    >
      <template #extra>
        <div class="result-actions">
          <el-button type="primary" @click="router.push('/learning/path')">查看学习路线</el-button>
          <el-button @click="router.push('/learning')">查看学习中心</el-button>
          <el-button @click="reset">再次测试</el-button>
        </div>
      </template>
    </el-result>

    <el-card v-if="completed && completedAbilityMap.length > 0" shadow="never" class="result-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">本次能力画像</div>
            <div class="card-subtitle">以下结果已同步到学习中心，并用于更新学习路线。</div>
          </div>
        </div>
      </template>

      <div class="result-list">
        <div v-for="item in completedAbilityMap" :key="item.knowledgePointId" class="result-item">
          <div class="result-meta">
            <div class="result-name">{{ item.knowledgePointName }}</div>
            <div class="result-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
          </div>
          <el-progress
            :percentage="Math.round(item.masteryLevel * 100)"
            :color="masteryColor(item.masteryLevel)"
            :stroke-width="8"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { courseApi } from '@/api/course'
import type { AbilityMap, AbilityTestQuestion, KnowledgePointNode } from '@/types'
import { learnApi } from '@/api/learn'

type KnowledgePointOption = Pick<KnowledgePointNode, 'id' | 'nodeType' | 'path'>

const router = useRouter()
const sessionId = ref<number | null>(null)
const selectedKnowledgePointId = ref<number | null>(null)
const selectedAnswer = ref<'A' | 'B' | 'C' | 'D' | ''>('')
const currentQuestion = ref<AbilityTestQuestion | null>(null)
const completed = ref(false)
const loading = ref(false)
const knowledgePointTree = ref<KnowledgePointNode[]>([])
const completedAbilityMap = ref<AbilityMap[]>([])

const knowledgePointOptions = computed<KnowledgePointOption[]>(() => {
  const items: KnowledgePointOption[] = []
  const walk = (nodes: KnowledgePointNode[]) => {
    nodes.forEach((node) => {
      if (node.active !== false) {
        items.push({ id: node.id, nodeType: node.nodeType, path: node.path })
      }
      if (node.children?.length) walk(node.children)
    })
  }
  walk(knowledgePointTree.value)
  return items
})

const completedSummary = computed(() => {
  if (completedAbilityMap.value.length === 0) {
    return '本次测试结果已保存，学习路线会根据最新数据更新。'
  }
  const weakest = [...completedAbilityMap.value]
    .sort((left, right) => left.masteryLevel - right.masteryLevel)
    .slice(0, 2)
    .map((item) => item.knowledgePointName)
  return weakest.length > 0
    ? `当前建议优先补强：${weakest.join('、')}`
    : '本次测试结果已保存，学习路线会根据最新数据更新。'
})

function knowledgeTypeLabel(type: KnowledgePointNode['nodeType']) {
  return { SUBJECT: '学科', DOMAIN: '知识领域', POINT: '知识点' }[type] ?? type
}

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

async function loadKnowledgePoints() {
  knowledgePointTree.value = await courseApi.listKnowledgePointTree({ activeOnly: true })
  if (knowledgePointOptions.value.length === 0) {
    knowledgePointTree.value = await courseApi.listKnowledgePointTree({ activeOnly: false })
  }
}

async function startTest() {
  if (!selectedKnowledgePointId.value) return
  loading.value = true
  try {
    const response = await learnApi.startAbilityTest(selectedKnowledgePointId.value)
    sessionId.value = response.sessionId
    currentQuestion.value = response.question
    completed.value = false
    completedAbilityMap.value = []
  } finally {
    loading.value = false
  }
}

async function submitAnswer() {
  if (!sessionId.value || !currentQuestion.value || !selectedAnswer.value) return
  loading.value = true
  try {
    const response = await learnApi.submitAnswer(sessionId.value, currentQuestion.value.id, selectedAnswer.value)
    selectedAnswer.value = ''
    if (response.completed) {
      completed.value = true
      currentQuestion.value = null
      completedAbilityMap.value = response.abilityMap ?? []
      ElMessage.success('能力图谱与学习路线已更新')
      return
    }
    currentQuestion.value = response.nextQuestion ?? null
  } finally {
    loading.value = false
  }
}

function reset() {
  sessionId.value = null
  selectedKnowledgePointId.value = null
  selectedAnswer.value = ''
  currentQuestion.value = null
  completed.value = false
  completedAbilityMap.value = []
}

onMounted(async () => {
  await loadKnowledgePoints()
})
</script>

<style scoped>
.setup-card,
.question-card,
.result-card {
  max-width: 880px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.card-subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.header-progress {
  width: 180px;
  flex-shrink: 0;
}

.option-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.setup-tips {
  color: #606266;
  line-height: 1.8;
}

.setup-tips p {
  margin: 0;
}

.question-content {
  padding: 18px 20px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #303133;
  line-height: 1.8;
  font-size: 15px;
}

.options {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.option-item:hover,
.option-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.12);
}

.option-key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #e4e7ed;
  color: #606266;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.option-item.selected .option-key {
  background: #409eff;
  color: #fff;
}

.option-text {
  color: #303133;
  line-height: 1.7;
}

.question-actions,
.result-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.result-list {
  display: grid;
  gap: 14px;
}

.result-item {
  display: grid;
  gap: 10px;
}

.result-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.result-path {
  font-size: 12px;
  color: #909399;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
  }

  .header-progress {
    width: 100%;
  }

  .question-actions,
  .result-actions {
    flex-direction: column;
  }
}
</style>

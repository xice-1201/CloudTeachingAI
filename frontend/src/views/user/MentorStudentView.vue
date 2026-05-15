<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div class="page-title-wrap">
        <el-button text :icon="ArrowLeft" @click="router.push('/mentor')">返回</el-button>
        <span class="page-title">{{ student?.username || '学生详情' }}</span>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" header="个人能力画像">
          <el-empty v-if="!abilityMap.length" description="该学生暂无能力画像数据" />
          <div v-else class="ability-list">
            <div v-for="item in sortedAbilityMap" :key="item.knowledgePointId" class="ability-item">
              <div class="ability-head">
                <div>
                  <div class="ability-name">{{ item.knowledgePointName }}</div>
                  <div class="ability-path">{{ item.knowledgePointPath || item.knowledgePointName }}</div>
                </div>
                <strong>{{ Math.round(item.masteryLevel * 100) }}%</strong>
              </div>
              <el-progress
                :percentage="Math.round(item.masteryLevel * 100)"
                :stroke-width="10"
                :color="masteryColor(item.masteryLevel)"
              />
              <div class="ability-meta">
                <span>测试 {{ Math.round(item.testScore * 100) }}%</span>
                <span>学习 {{ Math.round(item.progressScore * 100) }}%</span>
                <span>{{ item.resourceCount }} 个资源</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="never" header="学习建议">
          <div class="student-card" v-if="student">
            <el-avatar :size="48" :src="student.avatar">{{ student.username?.[0]?.toUpperCase() }}</el-avatar>
            <div>
              <div class="student-name">{{ student.username }}</div>
              <div class="student-email">{{ student.email }}</div>
            </div>
          </div>
          <el-input
            ref="adviceInputRef"
            v-model="adviceContent"
            type="textarea"
            :rows="9"
            maxlength="2000"
            show-word-limit
            placeholder="给学生写一段具体、可执行的学习建议"
          />
          <div class="advice-actions">
            <el-button :loading="suggesting" @click="generateAdvice">AI 智能教学</el-button>
            <el-button type="primary" :loading="submitting" @click="sendAdvice">发送系统消息</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { learnApi } from '@/api/learn'
import { userApi } from '@/api/user'
import type { AbilityMap, User } from '@/types'

const route = useRoute()
const router = useRouter()
const student = ref<User | null>(null)
const abilityMap = ref<AbilityMap[]>([])
const adviceContent = ref('')
const loading = ref(false)
const submitting = ref(false)
const suggesting = ref(false)
const adviceInputRef = ref()

const sortedAbilityMap = computed(() => [...abilityMap.value].sort((left, right) => left.masteryLevel - right.masteryLevel))

function masteryColor(level: number) {
  if (level >= 0.8) return '#67c23a'
  if (level >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function parseStudentId() {
  const id = Number(route.params.studentId)
  return Number.isFinite(id) && id > 0 ? id : null
}

async function loadStudentContext() {
  const studentId = parseStudentId()
  if (!studentId) {
    ElMessage.error('学生信息无效')
    await router.push('/mentor')
    return
  }
  loading.value = true
  try {
    const [relations, portrait] = await Promise.all([
      userApi.getMentorRelations(),
      learnApi.getMentoredStudentAbilityMap(studentId).catch(() => []),
    ])
    student.value = (relations.students ?? []).find((item) => item.id === studentId) ?? null
    if (!student.value) {
      ElMessage.error('只能查看自己的指导学生')
      await router.push('/mentor')
      return
    }
    abilityMap.value = portrait
  } finally {
    loading.value = false
  }
}

async function generateAdvice() {
  suggesting.value = true
  try {
    const weakPoints = sortedAbilityMap.value.slice(0, 3)
    const strongPoints = [...abilityMap.value].sort((left, right) => right.masteryLevel - left.masteryLevel).slice(0, 2)
    if (!weakPoints.length) {
      adviceContent.value = '目前你的能力画像数据还不充分，建议先完成一次能力测试，并优先学习课程中的基础资源，形成可分析的学习记录。'
      return
    }
    const weakText = weakPoints.map((item) => `${item.knowledgePointName}（${Math.round(item.masteryLevel * 100)}%）`).join('、')
    const strongText = strongPoints.map((item) => item.knowledgePointName).join('、') || '已有基础'
    adviceContent.value = [
      `结合当前能力画像，你在 ${weakText} 上还需要重点补强。`,
      `建议本周先复习这些知识点对应的基础资源，再完成 1 次小范围能力测试，检查是否真正掌握。`,
      `你在 ${strongText} 方面已有一定基础，可以把这些优势迁移到薄弱知识点的练习中。`,
      '学习时建议记录每次出错的题型和原因，下次交流时我们可以针对这些卡点继续调整学习路径。',
    ].join('\n')
  } finally {
    suggesting.value = false
  }
}

async function sendAdvice() {
  const studentId = parseStudentId()
  const content = adviceContent.value.trim()
  if (!studentId || !content) {
    ElMessage.warning('请先填写学习建议')
    return
  }
  submitting.value = true
  try {
    await userApi.sendMentorAdvice(studentId, content)
    adviceContent.value = ''
    ElMessage.success('学习建议已通过系统消息发送给学生')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadStudentContext()
  if (route.query.reply === '1') {
    await nextTick()
    adviceInputRef.value?.focus?.()
  }
})
</script>

<style scoped>
.page-title-wrap,
.student-card,
.ability-head,
.ability-meta,
.advice-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title-wrap,
.ability-head {
  justify-content: space-between;
}

.student-card {
  margin-bottom: 16px;
}

.student-name,
.ability-name {
  color: #303133;
  font-weight: 600;
}

.student-email,
.ability-path,
.ability-meta {
  color: #909399;
  font-size: 12px;
}

.ability-list {
  display: grid;
  gap: 16px;
}

.ability-item {
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.ability-head {
  margin-bottom: 10px;
}

.ability-meta {
  justify-content: flex-start;
  margin-top: 8px;
  flex-wrap: wrap;
}

.advice-actions {
  justify-content: flex-end;
  margin-top: 12px;
  flex-wrap: wrap;
}
</style>

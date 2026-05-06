<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">导师关系</span>
    </div>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never" header="我的导师">
          <template v-if="mentor">
            <div class="mentor-card">
              <el-avatar :size="64" :src="mentor.avatar">
                {{ mentor.username?.[0]?.toUpperCase() }}
              </el-avatar>
              <div class="mentor-info">
                <div class="mentor-name">{{ mentor.username }}</div>
                <div class="mentor-email">{{ mentor.email }}</div>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无导师" :image-size="80" />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" header="申请导师">
          <el-form inline>
            <el-form-item>
              <el-input v-model="keyword" placeholder="搜索教师" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchTeachers">搜索</el-button>
            </el-form-item>
          </el-form>

          <div v-loading="loading" style="margin-top: 16px">
            <div v-for="t in teachers" :key="t.id" class="teacher-item">
              <el-avatar :size="40" :src="t.avatar">{{ t.username?.[0]?.toUpperCase() }}</el-avatar>
              <div class="teacher-info">
                <div class="teacher-name">{{ t.username }}</div>
                <div class="teacher-email">{{ t.email }}</div>
              </div>
              <el-button size="small" @click="applyMentor(t.id)">申请</el-button>
            </div>
            <el-empty v-if="!loading && teachers.length === 0" description="暂无结果" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import type { User } from '@/types'

const mentor = ref<User | null>(null)
const teachers = ref<User[]>([])
const keyword = ref('')
const loading = ref(false)

async function searchTeachers() {
  loading.value = true
  try {
    const res = await userApi.listTeachers({ page: 1, pageSize: 10, keyword: keyword.value })
    teachers.value = res.items
  } finally {
    loading.value = false
  }
}

async function applyMentor(mentorId: number) {
  await userApi.applyMentor(mentorId)
  ElMessage.success('申请已发送')
}

onMounted(async () => {
  const relations = await userApi.getMentorRelations()
  mentor.value = relations.mentor ?? null
})
</script>

<style scoped>
.mentor-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
}
.mentor-info { flex: 1; }
.mentor-name { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.mentor-email { font-size: 13px; color: #909399; }
.teacher-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}
.teacher-item:last-child { border-bottom: none; }
.teacher-info { flex: 1; }
.teacher-name { font-size: 14px; font-weight: 600; color: #303133; }
.teacher-email { font-size: 12px; color: #909399; margin-top: 2px; }
</style>

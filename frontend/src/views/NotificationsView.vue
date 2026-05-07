<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">通知中心</span>
      <el-button text @click="markAll">全部已读</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <div v-for="n in notifications" :key="n.id" class="notify-item" :class="{ unread: !n.read }" @click="handleClick(n)">
        <div class="notify-dot" v-if="!n.read" />
        <div class="notify-body">
          <div class="notify-title">{{ n.title }}</div>
          <div class="notify-content">{{ n.content }}</div>
          <div class="notify-footer">
            <span class="notify-time">{{ formatDate(n.createdAt) }}</span>
            <span v-if="notifyStore.resolveNotificationUrl(n)" class="notify-action">查看详情</span>
          </div>
        </div>
        <el-tag :type="typeTag(n.type)" size="small">{{ typeLabel(n.type) }}</el-tag>
      </div>
      <el-empty v-if="!loading && notifications.length === 0" description="暂无通知" />
    </el-card>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="fetchData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useNotifyStore } from '@/store/notify'
import type { Notification } from '@/types'

const notifyStore = useNotifyStore()
const notifications = ref<Notification[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)

function formatDate(d: string) { return new Date(d).toLocaleString('zh-CN') }
function typeTag(t: string) { return { SYSTEM: 'info', COURSE: 'success', ASSIGNMENT: 'warning', GRADE: 'primary' }[t] ?? 'info' }
function typeLabel(t: string) { return { SYSTEM: '系统', COURSE: '课程', ASSIGNMENT: '作业', GRADE: '成绩' }[t] ?? t }

async function handleClick(n: Notification) {
  await notifyStore.openNotification(n)
}

async function markAll() {
  await notifyStore.markAllAsRead()
  notifications.value.forEach((n) => (n.read = true))
}

async function fetchData() {
  loading.value = true
  try {
    const res = await notifyStore.fetchNotifications({ page: page.value, pageSize: 20 })
    notifications.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
  position: relative;
}
.notify-item:last-child { border-bottom: none; }
.notify-item.unread { background: #fafcff; }
.notify-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
  margin-top: 6px;
}
.notify-body { flex: 1; }
.notify-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.notify-content { font-size: 13px; color: #606266; margin-bottom: 6px; line-height: 1.5; }
.notify-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}
.notify-time { color: #c0c4cc; }
.notify-action {
  color: #409eff;
  font-weight: 600;
}
</style>

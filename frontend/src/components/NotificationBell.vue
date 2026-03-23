<template>
  <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-bell">
    <el-button circle text @click="handleClick">
      <el-icon :size="20">
        <Bell />
      </el-icon>
    </el-button>
  </el-badge>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useNotifyStore } from '@/store/notify'

const router = useRouter()
const notifyStore = useNotifyStore()

const unreadCount = computed(() => notifyStore.unreadCount)

function handleClick() {
  router.push('/notifications')
}
</script>

<style scoped>
.notification-bell {
  cursor: pointer;
}

.notification-bell :deep(.el-badge__content) {
  border: none;
}
</style>

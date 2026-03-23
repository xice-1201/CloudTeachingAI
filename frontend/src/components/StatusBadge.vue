<template>
  <div class="status-badge" :class="`status-${type}`">
    <el-icon v-if="showIcon" class="status-icon">
      <component :is="iconComponent" />
    </el-icon>
    <span class="status-text">{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SuccessFilled, WarningFilled, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'

interface Props {
  type?: 'success' | 'warning' | 'danger' | 'info'
  text: string
  showIcon?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  showIcon: true,
})

const iconComponent = computed(() => {
  const iconMap = {
    success: SuccessFilled,
    warning: WarningFilled,
    danger: CircleCloseFilled,
    info: InfoFilled,
  }
  return iconMap[props.type]
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-icon {
  font-size: 14px;
}

.status-success {
  background-color: #f0f9ff;
  color: #67c23a;
}

.status-warning {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.status-danger {
  background-color: #fef0f0;
  color: #f56c6c;
}

.status-info {
  background-color: #f4f4f5;
  color: #909399;
}
</style>

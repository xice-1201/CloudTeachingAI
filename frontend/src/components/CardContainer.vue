<template>
  <div v-loading="loading" class="card-container">
    <div v-if="title || $slots.header" class="card-header">
      <slot name="header">
        <div class="card-title">{{ title }}</div>
      </slot>
      <div v-if="$slots.extra" class="card-extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="card-body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="card-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  loading?: boolean
}

withDefaults(defineProps<Props>(), {
  loading: false,
})
</script>

<style scoped>
.card-container {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e4e7ed;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.card-body {
  padding: 20px;
}

.card-footer {
  padding: 12px 20px;
  border-top: 1px solid #e4e7ed;
  background-color: #fafafa;
}
</style>

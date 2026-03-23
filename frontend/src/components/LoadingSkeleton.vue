<template>
  <div class="loading-container">
    <el-skeleton v-if="loading" :rows="rows" animated />
    <slot v-else />
  </div>
</template>

<script setup lang="ts">
interface Props {
  loading: boolean
  rows?: number
}

withDefaults(defineProps<Props>(), {
  rows: 5,
})
</script>

<style scoped>
.loading-container {
  width: 100%;
}
</style>

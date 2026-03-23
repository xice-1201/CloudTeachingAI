<template>
  <el-empty v-if="!loading && (!data || data.length === 0)" :description="emptyText" />
  <div v-else>
    <slot :data="data" :loading="loading" />
    <el-pagination
      v-if="showPagination && total > 0"
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :total="total"
      :page-sizes="pageSizes"
      :layout="layout"
      :background="background"
      class="pagination"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  data?: any[]
  total?: number
  page?: number
  pageSize?: number
  loading?: boolean
  showPagination?: boolean
  pageSizes?: number[]
  layout?: string
  background?: boolean
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  total: 0,
  page: 1,
  pageSize: 20,
  loading: false,
  showPagination: true,
  pageSizes: () => [10, 20, 50, 100],
  layout: 'total, sizes, prev, pager, next, jumper',
  background: true,
  emptyText: '暂无数据',
})

const emit = defineEmits<{
  pageChange: [page: number]
  sizeChange: [size: number]
}>()

const currentPage = ref(props.page)
const currentPageSize = ref(props.pageSize)

watch(
  () => props.page,
  (val) => {
    currentPage.value = val
  }
)

watch(
  () => props.pageSize,
  (val) => {
    currentPageSize.value = val
  }
)

function handlePageChange(page: number) {
  emit('pageChange', page)
}

function handleSizeChange(size: number) {
  emit('sizeChange', size)
}
</script>

<style scoped>
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

/**
 * 分页 Hook
 */

import { ref, computed } from 'vue'

export interface PaginationOptions {
  page?: number
  pageSize?: number
  total?: number
}

export function usePagination(options: PaginationOptions = {}) {
  const page = ref(options.page ?? 1)
  const pageSize = ref(options.pageSize ?? 20)
  const total = ref(options.total ?? 0)

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  const hasNext = computed(() => page.value < totalPages.value)
  const hasPrev = computed(() => page.value > 1)

  const setPage = (newPage: number) => {
    if (newPage >= 1 && newPage <= totalPages.value) {
      page.value = newPage
    }
  }

  const nextPage = () => {
    if (hasNext.value) {
      page.value++
    }
  }

  const prevPage = () => {
    if (hasPrev.value) {
      page.value--
    }
  }

  const setPageSize = (newSize: number) => {
    pageSize.value = newSize
    page.value = 1 // 重置到第一页
  }

  const setTotal = (newTotal: number) => {
    total.value = newTotal
  }

  const reset = () => {
    page.value = 1
    total.value = 0
  }

  return {
    page,
    pageSize,
    total,
    totalPages,
    hasNext,
    hasPrev,
    setPage,
    nextPage,
    prevPage,
    setPageSize,
    setTotal,
    reset,
  }
}

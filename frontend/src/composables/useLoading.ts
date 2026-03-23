/**
 * 加载状态 Hook
 */

import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useLoading() {
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const withLoading = async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
    loading.value = true
    error.value = null

    try {
      const result = await fn()
      return result
    } catch (err) {
      error.value = err as Error
      ElMessage.error((err as Error).message || '操作失败')
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    withLoading,
  }
}

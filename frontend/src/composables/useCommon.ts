/**
 * 通用 Composables
 */

import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 防抖 Hook
 */
export function useDebounce<T extends (...args: any[]) => any>(
  fn: T,
  delay = 300
): (...args: Parameters<T>) => void {
  let timer: number | null = null

  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = window.setTimeout(() => {
      fn(...args)
    }, delay)
  }
}

/**
 * 节流 Hook
 */
export function useThrottle<T extends (...args: any[]) => any>(
  fn: T,
  delay = 300
): (...args: Parameters<T>) => void {
  let lastTime = 0

  return (...args: Parameters<T>) => {
    const now = Date.now()
    if (now - lastTime >= delay) {
      fn(...args)
      lastTime = now
    }
  }
}

/**
 * 页面可见性 Hook
 */
export function usePageVisibility() {
  const isVisible = ref(!document.hidden)

  const handleVisibilityChange = () => {
    isVisible.value = !document.hidden
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { isVisible }
}

/**
 * 网络状态 Hook
 */
export function useOnline() {
  const isOnline = ref(navigator.onLine)

  const handleOnline = () => {
    isOnline.value = true
  }

  const handleOffline = () => {
    isOnline.value = false
  }

  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
  })

  onUnmounted(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return { isOnline }
}

/**
 * 窗口大小 Hook
 */
export function useWindowSize() {
  const width = ref(window.innerWidth)
  const height = ref(window.innerHeight)

  const handleResize = () => {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
  })

  return { width, height }
}

/**
 * 倒计时 Hook
 */
export function useCountdown(initialSeconds: number) {
  const seconds = ref(initialSeconds)
  const isRunning = ref(false)
  let timer: number | null = null

  const start = () => {
    if (isRunning.value) return
    isRunning.value = true

    timer = window.setInterval(() => {
      if (seconds.value > 0) {
        seconds.value--
      } else {
        stop()
      }
    }, 1000)
  }

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    isRunning.value = false
  }

  const reset = (newSeconds?: number) => {
    stop()
    seconds.value = newSeconds ?? initialSeconds
  }

  onUnmounted(() => {
    stop()
  })

  return { seconds, isRunning, start, stop, reset }
}

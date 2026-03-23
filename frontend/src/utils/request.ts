import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Token 刷新状态
let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token))
  refreshSubscribers = []
}

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, message, data } = response.data

    // 兼容后端返回 code: 0 表示成功的情况
    if (code === 0 || code === 200) {
      return data
    } else {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message || '请求失败'))
    }
  },
  async (error) => {
    const originalRequest = error.config

    if (error.response) {
      const { status, data } = error.response

      // Token 过期，尝试刷新
      if (status === 401 && data?.code === 40102 && !originalRequest._retry) {
        if (isRefreshing) {
          // 正在刷新，将请求加入队列
          return new Promise((resolve) => {
            subscribeTokenRefresh((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(request(originalRequest))
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const refreshToken = localStorage.getItem('refreshToken')
          if (!refreshToken) {
            throw new Error('No refresh token')
          }

          const { data: newToken } = await axios.post(
            `${request.defaults.baseURL}/auth/refresh`,
            { refreshToken }
          )

          localStorage.setItem('token', newToken.token)
          if (newToken.refreshToken) {
            localStorage.setItem('refreshToken', newToken.refreshToken)
          }

          onTokenRefreshed(newToken.token)
          originalRequest.headers.Authorization = `Bearer ${newToken.token}`
          return request(originalRequest)
        } catch (refreshError) {
          // 刷新失败，清除登录态
          localStorage.removeItem('token')
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('userRole')
          ElMessage.error('登录已过期，请重新登录')
          router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      // 其他错误处理
      switch (status) {
        case 401:
          if (!originalRequest._retry) {
            ElMessage.error('未授权，请重新登录')
            localStorage.removeItem('token')
            localStorage.removeItem('refreshToken')
            localStorage.removeItem('userRole')
            router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
          }
          break
        case 403:
          ElMessage.error(data?.message || '没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 429:
          ElMessage.error('请求过于频繁，请稍后再试')
          break
        case 500:
          ElMessage.error('服务器错误，请稍后重试')
          break
        case 503:
          ElMessage.error('服务暂时不可用，请稍后重试')
          break
        default:
          ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络连接')
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

export default request

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

let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token))
  refreshSubscribers = []
}

function readHeaderFlag(config: InternalAxiosRequestConfig | any, headerName: string) {
  const directValue = config?.headers?.[headerName]
  const lowerCaseValue = config?.headers?.[headerName.toLowerCase()]
  return directValue === 'true' || directValue === true || lowerCaseValue === 'true' || lowerCaseValue === true
}

function isSilentError(config?: InternalAxiosRequestConfig | any) {
  return readHeaderFlag(config, 'X-Silent-Error')
}

function shouldSkipAuthRedirect(config?: InternalAxiosRequestConfig | any) {
  return readHeaderFlag(config, 'X-Skip-Auth-Redirect')
}

function clearStoredSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userRole')
  localStorage.removeItem('userInfo')
}

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, message, data } = response.data
    const silentError = isSilentError(response.config as InternalAxiosRequestConfig)

    if (code === 0 || code === 200) {
      return data
    }

    if (!silentError) {
      ElMessage.error(message || '请求失败')
    }
    return Promise.reject(new Error(message || '请求失败'))
  },
  async (error) => {
    const originalRequest = error.config
    const silentError = isSilentError(originalRequest)
    const skipAuthRedirect = shouldSkipAuthRedirect(originalRequest)

    if (error.response) {
      const { status, data } = error.response

      if (status === 401 && data?.code === 40102 && !originalRequest._retry) {
        if (isRefreshing) {
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

          const response = await axios.post(
            `${request.defaults.baseURL}/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`
          )

          const { data: loginData } = response.data

          localStorage.setItem('token', loginData.accessToken)
          if (loginData.refreshToken) {
            localStorage.setItem('refreshToken', loginData.refreshToken)
          }

          onTokenRefreshed(loginData.accessToken)
          originalRequest.headers.Authorization = `Bearer ${loginData.accessToken}`
          return request(originalRequest)
        } catch (refreshError) {
          clearStoredSession()
          if (!silentError) {
            ElMessage.error('登录已过期，请重新登录')
          }
          if (!skipAuthRedirect) {
            router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath, expired: '1' } })
          }
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      switch (status) {
        case 401:
          if (!originalRequest._retry) {
            clearStoredSession()
            if (!silentError) {
              ElMessage.error(data?.message || '未授权，请重新登录')
            }
            if (!skipAuthRedirect) {
              router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath, expired: '1' } })
            }
          }
          break
        case 403:
          if (!silentError) {
            ElMessage.error(data?.message || '没有权限访问')
          }
          break
        case 404:
          if (!silentError) {
            ElMessage.error(data?.message || '请求的资源不存在')
          }
          break
        case 429:
          if (!silentError) {
            ElMessage.error(data?.message || '请求过于频繁，请稍后再试')
          }
          break
        case 500:
          if (!silentError) {
            ElMessage.error(data?.message || '服务器错误，请稍后重试')
          }
          break
        case 503:
          if (!silentError) {
            ElMessage.error(data?.message || '服务暂时不可用，请稍后重试')
          }
          break
        default:
          if (!silentError) {
            ElMessage.error(data?.message || '请求失败')
          }
      }
    } else if (error.code === 'ECONNABORTED') {
      if (!silentError) {
        ElMessage.error('请求超时，请检查网络连接')
      }
    } else if (!silentError) {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

export default request

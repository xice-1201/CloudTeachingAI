import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

type ErrorPayload = {
  code?: number
  message?: string
  stackTrace?: string | null
  data?: unknown
}

type ApiError = Error & {
  code?: number
  payload?: ErrorPayload
  config?: InternalAxiosRequestConfig
  response?: AxiosResponse<ErrorPayload>
}

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
}

type WrappedResponse<T = unknown> = {
  code?: number
  message?: string
  data: T
  stackTrace?: string | null
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((callback) => callback(token))
  refreshSubscribers = []
}

function readHeaderFlag(config: InternalAxiosRequestConfig | undefined, headerName: string) {
  const directValue = config?.headers?.[headerName]
  const lowerCaseValue = config?.headers?.[headerName.toLowerCase()]
  return directValue === 'true' || directValue === true || lowerCaseValue === 'true' || lowerCaseValue === true
}

function isSilentError(config?: InternalAxiosRequestConfig) {
  return readHeaderFlag(config, 'X-Silent-Error')
}

function shouldSkipAuthRedirect(config?: InternalAxiosRequestConfig) {
  return readHeaderFlag(config, 'X-Skip-Auth-Redirect')
}

function clearStoredSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userRole')
  localStorage.removeItem('userInfo')
}

function buildErrorMessage(payload?: ErrorPayload, fallback = '请求失败') {
  if (payload?.message) {
    return payload.message
  }

  if (typeof payload?.code === 'number') {
    return `${fallback}（错误码 ${payload.code}）`
  }

  return fallback
}

function createApiError(
  message: string,
  payload?: ErrorPayload,
  config?: InternalAxiosRequestConfig,
  response?: AxiosResponse<ErrorPayload>,
) {
  const error = new Error(message) as ApiError
  error.name = 'ApiError'
  error.code = payload?.code
  error.payload = payload
  error.config = config
  error.response = response
  return error
}

function logBrowserError(error: unknown, payload?: ErrorPayload) {
  const apiError = error as ApiError
  const method = apiError?.config?.method?.toUpperCase?.() ?? 'UNKNOWN'
  const url = apiError?.config?.url ?? 'UNKNOWN_URL'
  const status = apiError?.response?.status ?? 'NO_STATUS'
  const message = apiError?.message ?? payload?.message ?? 'Unknown error'

  console.group(`[CloudTeachingAI Error] ${method} ${url} -> ${status}`)
  console.error('Summary:', { method, url, status, message, code: apiError?.code ?? payload?.code })
  console.error('Request config:', apiError?.config)

  if (payload) {
    console.error('Response payload:', payload)
    if (payload.stackTrace) {
      console.error('Server stack trace:\n' + payload.stackTrace)
    }
  } else if (apiError?.response?.data) {
    console.error('Response payload:', apiError.response.data)
  }

  console.error('Error object:', apiError)
  if (apiError?.stack) {
    console.error('Browser stack trace:\n' + apiError.stack)
  }
  console.trace('[CloudTeachingAI Error Trace]')
  console.groupEnd()
}

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response: AxiosResponse<WrappedResponse>) => {
    const payload: ErrorPayload = {
      code: response.data?.code,
      message: response.data?.message,
      data: response.data?.data,
      stackTrace: response.data?.stackTrace,
    }
    const silentError = isSilentError(response.config)

    if (payload.code === 0 || payload.code === 200) {
      return response.data.data
    }

    const businessError = createApiError(
      buildErrorMessage(payload, '业务请求失败'),
      payload,
      response.config,
      response as AxiosResponse<ErrorPayload>,
    )
    logBrowserError(businessError, payload)

    if (!silentError) {
      ElMessage.error(buildErrorMessage(payload, '请求失败'))
    }

    return Promise.reject(businessError)
  },
  async (error) => {
    const originalRequest = (error.config ?? {}) as RetryableRequestConfig
    const silentError = isSilentError(originalRequest)
    const skipAuthRedirect = shouldSkipAuthRedirect(originalRequest)
    const payload = error?.response?.data as ErrorPayload | undefined

    logBrowserError(error, payload)

    if (error.response) {
      const { status, data } = error.response as AxiosResponse<ErrorPayload>

      if (status === 401 && data?.code === 40102 && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve) => {
            subscribeTokenRefresh((token: string) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`
              }
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

          const refreshResponse = await axios.post<WrappedResponse<{
            accessToken: string
            refreshToken?: string
          }>>(
            `${request.defaults.baseURL}/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`,
          )

          const loginData = refreshResponse.data.data
          localStorage.setItem('token', loginData.accessToken)
          if (loginData.refreshToken) {
            localStorage.setItem('refreshToken', loginData.refreshToken)
          }

          onTokenRefreshed(loginData.accessToken)
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${loginData.accessToken}`
          }
          return request(originalRequest)
        } catch (refreshError) {
          clearStoredSession()
          logBrowserError(refreshError)
          if (!silentError) {
            ElMessage.error('登录已过期，请重新登录')
          }
          if (!skipAuthRedirect) {
            router.push({
              name: 'Login',
              query: { redirect: router.currentRoute.value.fullPath, expired: '1' },
            })
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
              ElMessage.error(buildErrorMessage(data, '未授权，请重新登录'))
            }
            if (!skipAuthRedirect) {
              router.push({
                name: 'Login',
                query: { redirect: router.currentRoute.value.fullPath, expired: '1' },
              })
            }
          }
          break
        case 403:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '没有权限访问'))
          }
          break
        case 404:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '请求的资源不存在'))
          }
          break
        case 429:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '请求过于频繁，请稍后再试'))
          }
          break
        case 500:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '服务端错误，请稍后重试'))
          }
          break
        case 503:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '服务暂时不可用，请稍后重试'))
          }
          break
        default:
          if (!silentError) {
            ElMessage.error(buildErrorMessage(data, '请求失败'))
          }
          break
      }
    } else if (error.code === 'ECONNABORTED') {
      if (!silentError) {
        ElMessage.error('请求超时，请检查网络连接')
      }
    } else if (!silentError) {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  },
)

export default request

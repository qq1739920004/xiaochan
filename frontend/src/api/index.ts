import axios from 'axios'
import { ElMessage } from 'element-plus'

function getToken(): string {
  return localStorage.getItem('token') || ''
}

const apiClient = axios.create({
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

apiClient.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code !== undefined && data.code !== 200) {
      const errorMsg = data.msg || data.message || '请求失败'
      ElMessage.error(errorMsg)
      return Promise.reject(new Error(errorMsg))
    }
    return response
  },
  (error) => {
    let errorMsg = '网络错误，请稍后重试'
    if (error.response) {
      errorMsg = `请求失败: ${error.response.status}`
      if (error.response.status === 401) {
        errorMsg = 'Token 无效或已过期，请重新登录'
      }
    } else if (error.message) {
      errorMsg = error.message
    }
    ElMessage.error(errorMsg)
    return Promise.reject(error)
  },
)

const api = {
  get(url: string, params?: Record<string, unknown>, config?: Record<string, unknown>) {
    return apiClient.get(url, { params, ...config })
  },
  post(url: string, data?: unknown, config?: Record<string, unknown>) {
    return apiClient.post(url, data, config)
  },
  put(url: string, data?: unknown, config?: Record<string, unknown>) {
    return apiClient.put(url, data, config)
  },
  delete(url: string, config?: Record<string, unknown>) {
    return apiClient.delete(url, config)
  },
  postForm(url: string, data: Record<string, unknown>, config?: Record<string, unknown>) {
    const formData = new URLSearchParams()
    for (const key in data) {
      if (Object.prototype.hasOwnProperty.call(data, key)) {
        formData.append(key, String(data[key]))
      }
    }
    return apiClient.post(url, formData, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      ...config,
    })
  },
}

export default api

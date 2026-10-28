import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('dorm_admin_token')
  if (token) {
    config.headers['Authorization'] = token
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('dorm_admin_token')
      localStorage.removeItem('dorm_admin_user')
      localStorage.removeItem('dorm_managed_building')
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    ElMessage.error(error.message || '网络连接异常，请确认后端服务已启动')
    return Promise.reject(error)
  }
)

export async function downloadExport(path, filename) {
  const response = await fetch(`/api/export/${path}`, {
    headers: { Authorization: localStorage.getItem('dorm_admin_token') || '' }
  })
  if (!response.ok) throw new Error('导出失败，请检查登录状态和权限')
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export default request
